package se.svt.oss.encore.service.segmentedencode

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.AudioEncodingMode
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.SegmentedEncodingInfo
import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.AudioEncoder
import se.svt.oss.encore.model.profile.OutputProducer
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.profile.SimpleAudioEncode
import se.svt.oss.encore.model.profile.VideoEncode
import se.svt.oss.encore.model.queue.Task
import se.svt.oss.encore.model.queue.TaskType
import se.svt.oss.encore.process.segmentedEncodingInfoOrThrow
import se.svt.oss.encore.process.targetFilenameFromSegmentFilename
import se.svt.oss.encore.service.FfmpegExecutor
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.encore.service.profile.ProfileService
import se.svt.oss.encore.util.allAudioEncodes
import se.svt.oss.encore.util.hasAudioEncodes
import se.svt.oss.encore.util.hasVideoEncodes
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import kotlin.math.ceil

private val log = KotlinLogging.logger {}

fun OutputProducer?.audioSamplerates(): List<Int> = when {
    this == null -> emptyList()
    !this.enabled -> emptyList()
    this is AudioEncode -> listOf(this.samplerate)
    this is SimpleAudioEncode -> this.samplerate?.let { listOf(it) } ?: emptyList()
    this is VideoEncode -> this.audioEncodes.flatMap { it.audioSamplerates() } + this.audioEncode.audioSamplerates()
    else -> emptyList()
}

data class AudioEncodingConfig(
    val audioEncodingMode: AudioEncodingMode,
    val audioSegmentPadding: Double,
    val audioSegmentLength: Double,
    val numSegments: Int,
)

@Service
class SegmentedEncodeService(
    private val ffmpegExecutor: FfmpegExecutor,
    private val mediaAnalyzerService: MediaAnalyzerService,
    private val profileService: ProfileService,
    private val encoreProperties: EncoreProperties,
) {

    fun segmentedEncodingInfo(encoreJob: EncoreJob): SegmentedEncodingInfo? {
        val profile = profileService.getProfile(encoreJob)
        val hasVideo = profile.hasVideoEncodes()
        if (encoreJob.segmentLength == null && hasVideo) {
            return null
        }
        if (encoreJob.segmentLength == null &&
            encoreJob.audioEncodingMode != AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED
        ) {
            return null
        }
        val segmentLength = encoreJob.segmentLength ?: 0.0
        val audioEncodingConfig = audioEncodingConfig(encoreJob, profile)
        val numVideoSegments = if (hasVideo) numSegments(encoreJob, segmentLength) else 0
        val numTasks = numVideoSegments + audioEncodingConfig.numSegments
        return SegmentedEncodingInfo(
            segmentLength = segmentLength,
            audioEncodingMode = audioEncodingConfig.audioEncodingMode,
            numTasks = numTasks,
            numSegments = numVideoSegments,
            numAudioSegments = audioEncodingConfig.numSegments,
            audioSegmentPadding = audioEncodingConfig.audioSegmentPadding,
            audioSegmentLength = audioEncodingConfig.audioSegmentLength,
        )
    }

    fun audioEncodingConfig(encoreJob: EncoreJob, profile: Profile): AudioEncodingConfig {
        if (!profile.hasAudioEncodes()) {
            log.info { "No audio encodes found in profile ${profile.name}, skipping audio segmented encoding configuration." }
            return AudioEncodingConfig(AudioEncodingMode.ENCODE_WITH_VIDEO, 0.0, 0.0, 0)
        }

        val audioSampleRates = profile.encodes
            .filter { it.enabled }
            .flatMap { it.audioSamplerates() }.distinct()

        val audioEncodingMode = audioEncodingMode(encoreJob, profile, audioSampleRates)

        if (audioEncodingMode != AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED) {
            val numAudioSegments =
                if (audioEncodingMode == AudioEncodingMode.ENCODE_SEPARATELY_FULL) {
                    1
                } else {
                    0
                }
            return AudioEncodingConfig(audioEncodingMode, 0.0, 0.0, numAudioSegments)
        }

        // Each audio segment is padded with two audio frames at start and end
        // This padding is then removed when joining segments
        // This avoids priming samples causing artifacts at segment boundaries
        // Calculate audio segment padding: 2 * (frame_size / sample_rate)
        val audioFrameSize = 1024.0 // Standard AAC frame size in samples
        val maxSampleRate = audioSampleRates.maxOrNull() ?: 48000
        val audioSegmentPadding = 2.0 * audioFrameSize / maxSampleRate

        // Calculate audio segment length for ENCODE_SEPARATELY_SEGMENTED mode
        val audioSegmentLength =
            encoreJob.audioSegmentLength ?: run {
                // Calculate a value close to 256s that is a multiple of the audio frame size
                // 256 is selected because it is an integer number of audio frames for both
                // 44.1kHz and 48kHz sample rates.
                val frameDuration = audioFrameSize / maxSampleRate
                val targetDuration = 256.0
                val numFrames = kotlin.math.round(targetDuration / frameDuration).toLong()
                numFrames * frameDuration
            }

        return AudioEncodingConfig(
            audioEncodingMode,
            audioSegmentPadding,
            audioSegmentLength,
            numSegments(encoreJob, audioSegmentLength),
        )
    }

    fun audioEncodingMode(encoreJob: EncoreJob, profile: Profile, audioSampleRates: List<Int>): AudioEncodingMode {
        // Get the requested audio encoding mode from job or fall back to default
        val requestedMode = encoreJob.audioEncodingMode
            ?: encoreProperties.encoding.segmentedEncoding.audioEncodingMode

        val hasNonAacAudioEncode = profile.allAudioEncodes()
            .filterNot { it.isAacEncoder() }
            .isNotEmpty()

        return when {
            requestedMode == AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED && audioSampleRates.size > 1 -> {
                log.warn { "Multiple audio sample rates detected (${audioSampleRates.joinToString()}), downgrading from ENCODE_SEPARATELY_SEGMENTED to ENCODE_SEPARATELY_FULL" }
                AudioEncodingMode.ENCODE_SEPARATELY_FULL
            }
            requestedMode == AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED && hasNonAacAudioEncode -> {
                log.warn { "Non-AAC audio encode detected, downgrading from ENCODE_SEPARATELY_SEGMENTED to ENCODE_SEPARATELY_FULL" }
                AudioEncodingMode.ENCODE_SEPARATELY_FULL
            }
            else -> requestedMode
        }
    }

    private fun AudioEncoder.isAacEncoder(): Boolean = this.codec == "aac" || this.codec == "libfdk_aac"

    private fun numSegments(encoreJob: EncoreJob, segmentLength: Double): Int {
        val readDuration = encoreJob.duration
        return if (readDuration != null) {
            ceil(readDuration / segmentLength).toInt()
        } else {
            val segments =
                encoreJob.inputs.map { ceil(((it.analyzed as MediaContainer).duration - (it.seekTo ?: 0.0)) / segmentLength).toInt() }
                    .toSet()
            if (segments.size > 1) {
                throw RuntimeException("Inputs differ in length")
            }
            segments.first()
        }
    }

    /**
     * Create tasks for segmented encoding based on the audio encoding mode.
     * - ENCODE_WITH_VIDEO: N tasks of type AUDIOVIDEOSEGMENT
     * - ENCODE_SEPARATELY_FULL: 1 AUDIOFULL task + N VIDEOSEGMENT tasks
     * - ENCODE_SEPARATELY_SEGMENTED: M AUDIOSEGMENT tasks + N VIDEOSEGMENT tasks
     */
    fun createTasks(encoreJob: EncoreJob): List<Task> {
        val segmentedEncodingInfo = encoreJob.segmentedEncodingInfoOrThrow()
        val audioEncodingMode = segmentedEncodingInfo.audioEncodingMode
        val numSegments = segmentedEncodingInfo.numSegments

        log.debug { "Encoding using $numSegments video segments, ${segmentedEncodingInfo.numAudioSegments} audio segments,`0 with audio mode: $audioEncodingMode" }

        val tasks = mutableListOf<Task>()
        var taskNo = 0

        when (audioEncodingMode) {
            AudioEncodingMode.ENCODE_WITH_VIDEO -> {
                // Audio and video together in each segment
                repeat(numSegments) { segmentIndex ->
                    tasks.add(
                        Task(
                            type = TaskType.AUDIOVIDEOSEGMENT,
                            taskNo = taskNo++,
                            segment = segmentIndex,
                        ),
                    )
                }
            }
            AudioEncodingMode.ENCODE_SEPARATELY_FULL -> {
                // One full audio task
                tasks.add(
                    Task(
                        type = TaskType.AUDIOFULL,
                        taskNo = taskNo++,
                        segment = 0,
                    ),
                )
                // N video segment tasks
                repeat(numSegments) { segmentIndex ->
                    tasks.add(
                        Task(
                            type = TaskType.VIDEOSEGMENT,
                            taskNo = taskNo++,
                            segment = segmentIndex,
                        ),
                    )
                }
            }
            AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED -> {
                val numAudioSegments = segmentedEncodingInfo.numAudioSegments
                // N audio segment tasks
                repeat(numAudioSegments) { segmentIndex ->
                    tasks.add(
                        Task(
                            type = TaskType.AUDIOSEGMENT,
                            taskNo = taskNo++,
                            segment = segmentIndex,
                        ),
                    )
                }
                // N video segment tasks
                repeat(numSegments) { segmentIndex ->
                    tasks.add(
                        Task(
                            type = TaskType.VIDEOSEGMENT,
                            taskNo = taskNo++,
                            segment = segmentIndex,
                        ),
                    )
                }
            }
        }
        return tasks
    }

    data class JoinSegmentOperation(
        val target: File,
        val segmentFiles: List<File>,
        val audioFile: File? = null,
        val audioSegmentFiles: List<File>? = null,
    )

    fun prepareJoinSegment(encoreJob: EncoreJob, sharedWorkDir: File): Map<String, JoinSegmentOperation> {
        val segmentedEncodingInfo = encoreJob.segmentedEncodingInfoOrThrow()
        val audioEncodingMode = segmentedEncodingInfo.audioEncodingMode

        // Groups video segment files by suffix/target file
        val videoSegmentFileMap = sharedWorkDir.listFiles()
            .filter { it.isFile }
            .sorted()
            .groupBy { encoreJob.targetFilenameFromSegmentFilename(it.name) }

        val audioFilesMap = if (audioEncodingMode == AudioEncodingMode.ENCODE_SEPARATELY_FULL) {
            // Full audio files are in the audio subfolder
            sharedWorkDir.resolve("audio").listFiles().filter { it.isFile }.associateBy { it.name }
        } else {
            emptyMap()
        }

        val audioSegmentFileMap = if (audioEncodingMode == AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED) {
            // Audio segments are in the audio subfolder
            val audioDir = sharedWorkDir.resolve("audio")
            audioDir.listFiles()
                .filter { it.isFile }
                .sorted()
                .groupBy { encoreJob.targetFilenameFromSegmentFilename(it.name) }
        } else {
            emptyMap()
        }

        val outputFolder = File(encoreJob.outputFolder)
        outputFolder.mkdirs()
        val joinSegmentOperations = LinkedHashMap<String, JoinSegmentOperation>()

        // Handle video segments and determine corresponding audio
        videoSegmentFileMap.forEach { (targetName, files) ->
            val targetFile = outputFolder.resolve(targetName)
            val audioFile = audioFilesMap[targetName]
            val audioSegmentFiles = audioSegmentFileMap[targetName]

            joinSegmentOperations[targetFile.name] = JoinSegmentOperation(
                target = targetFile,
                segmentFiles = files.map { sharedWorkDir.resolve(it) },
                audioFile = audioFile,
                audioSegmentFiles = audioSegmentFiles,
            )
        }

        // Handle audio-only outputs
        audioFilesMap.forEach { (targetName, audioFile) ->
            if (!joinSegmentOperations.containsKey(targetName)) {
                val targetFile = outputFolder.resolve(targetName)
                joinSegmentOperations[targetName] = JoinSegmentOperation(
                    target = targetFile,
                    segmentFiles = emptyList(),
                    audioFile = audioFile,
                    audioSegmentFiles = null,
                )
            }
        }
        audioSegmentFileMap.forEach { (targetName, audioSegmentFiles) ->
            if (!joinSegmentOperations.containsKey(targetName)) {
                val targetFile = outputFolder.resolve(targetName)
                joinSegmentOperations[targetName] = JoinSegmentOperation(
                    target = targetFile,
                    segmentFiles = emptyList(),
                    audioFile = null,
                    audioSegmentFiles = audioSegmentFiles,
                )
            }
        }

        return joinSegmentOperations
    }

    fun joinSegments(encoreJob: EncoreJob, sharedWorkDir: File): List<MediaFile> {
        val joinSegmentOperations = prepareJoinSegment(encoreJob, sharedWorkDir)

        return joinSegmentOperations.values.map { joinSegmentOperation ->
            joinSegments(encoreJob, sharedWorkDir, joinSegmentOperation)
        }
    }

    fun joinSegments(encoreJob: EncoreJob, sharedWorkDir: File, joinSegmentOperation: JoinSegmentOperation): MediaFile {
        log.info { "Joining segments for ${joinSegmentOperation.target.name}" }

        // Create audio segment list file if audio segments are present
        val audioSegmentListFile = if (joinSegmentOperation.audioSegmentFiles != null) {
            log.info { "Preparing to join ${joinSegmentOperation.audioSegmentFiles.size} audio segments with video" }
            val audioSegmentListFile = sharedWorkDir.resolve("audio/${joinSegmentOperation.target.nameWithoutExtension}_audio_filelist.txt")
            val segmentedInfo = encoreJob.segmentedEncodingInfoOrThrow()
            val padding = segmentedInfo.audioSegmentPadding
            val audioSegmentLength = segmentedInfo.audioSegmentLength
            val numSegments = joinSegmentOperation.audioSegmentFiles.size

            joinSegmentOperation.audioSegmentFiles.forEachIndexed { index, file ->
                audioSegmentListFile.appendText("file ${file.absolutePath}\n")

                // Add inpoint/outpoint to trim padding
                val isFirst = index == 0
                val isLast = index == numSegments - 1
                // Trim start padding for all segments except first
                val inPoint = if (isFirst) 0.0 else padding

                audioSegmentListFile.appendText("inpoint $inPoint\n")

                if (!isLast) {
                    // Trim end padding for all segments except last
                    val outpoint = if (isFirst) audioSegmentLength else audioSegmentLength + padding
                    audioSegmentListFile.appendText("outpoint $outpoint\n")
                }
            }
            audioSegmentListFile
        } else {
            null
        }

        // Join video segments with audio (or just copy audio if no video)
        return if (joinSegmentOperation.segmentFiles.isNotEmpty()) {
            val segmentListFile = sharedWorkDir.resolve("${joinSegmentOperation.target.nameWithoutExtension}_filelist.txt")
            joinSegmentOperation.segmentFiles.forEach { file ->
                segmentListFile.appendText("file ${file.absolutePath}\n")
            }
            ffmpegExecutor.joinSegments(
                encoreJob,
                segmentListFile,
                joinSegmentOperation.target,
                joinSegmentOperation.audioFile,
                audioSegmentListFile,
            )
        } else {
            // Audio-only output: either segments or full file
            if (audioSegmentListFile != null) {
                log.info { "Joining audio-only segments for ${joinSegmentOperation.target.name}" }
                ffmpegExecutor.joinSegments(encoreJob, audioSegmentListFile, joinSegmentOperation.target, null, null)
            } else {
                log.info { "Moving audio file ${joinSegmentOperation.audioFile} to output folder" }
                val target = joinSegmentOperation.target
                joinSegmentOperation.audioFile!!.copyTo(target, overwrite = true)
                mediaAnalyzerService.analyze(target.absolutePath)
            }
        }
    }
}
