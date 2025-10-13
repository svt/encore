package se.svt.oss.encore.service.segmentedencode

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.queue.Task
import se.svt.oss.encore.model.queue.TaskType
import se.svt.oss.encore.process.baseName
import se.svt.oss.encore.process.segmentSuffixFromFilename
import se.svt.oss.encore.process.segmentedEncodingInfoOrThrow
import se.svt.oss.encore.service.FfmpegExecutor
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File

private val log = KotlinLogging.logger {}

@Service
class SegmentedEncodeService(
    private val ffmpegExecutor: FfmpegExecutor,
    private val mediaAnalyzerService: MediaAnalyzerService,
) {

    /**
     * Create tasks for segmented encoding. One task will be created per segment. If audio is encoded separately,
     * an additional task for full audio encoding will be created.
     */
    fun createTasks(encoreJob: EncoreJob): List<Task> {
        val segmentedEncodingInfo = encoreJob.segmentedEncodingInfoOrThrow()

        val separateAudioEncode = !segmentedEncodingInfo.segmentedAudioEncode
        val numSegments = segmentedEncodingInfo.numSegments

        log.debug { "Encoding using $numSegments segments" }
        if (separateAudioEncode) {
            log.debug { "Encoding audio separately" }
        }

        val tasks = mutableListOf<Task>()

        var taskNo = 0
        if (separateAudioEncode) {
            tasks.add(
                Task(
                    type = TaskType.AUDIOFULL,
                    taskNo = taskNo++,
                    segment = 0,
                ),
            )
        }
        val segmentsTaskType = if (separateAudioEncode) {
            TaskType.VIDEOSEGMENT
        } else {
            TaskType.AUDIOVIDEOSEGMENT
        }
        repeat(numSegments) {
            tasks.add(
                Task(
                    type = segmentsTaskType,
                    taskNo = taskNo++,
                    segment = it,
                ),
            )
        }
        return tasks
    }

    data class JoinSegmentOperation(
        val target: File,
        val segmentFiles: List<File>,
        val audioFile: File? = null,
    )

    fun prepareJoinSegment(encoreJob: EncoreJob, sharedWorkDir: File): Map<String, JoinSegmentOperation> {
        val segmentedEncodingInfo = encoreJob.segmentedEncodingInfoOrThrow()
        val separateAudioEncode = !segmentedEncodingInfo.segmentedAudioEncode

        // Groups segment files by suffix/target file
        val segmentFileMap = sharedWorkDir.listFiles()
            .filter { it.isFile }
            .map { it.name }
            .sorted()
            .groupBy { encoreJob.segmentSuffixFromFilename(it) }

        val outputFolder = File(encoreJob.outputFolder)
        outputFolder.mkdirs()
        val joinSegmentOperations = LinkedHashMap<String, JoinSegmentOperation>()
        segmentFileMap.forEach { (suffix, files) ->
            val targetName = encoreJob.baseName + suffix
            val targetFile = outputFolder.resolve(targetName)
            val audioFile = if (separateAudioEncode) {
                sharedWorkDir.resolve("audio").resolve(targetName).takeIf { it.exists() }
            } else {
                null
            }
            joinSegmentOperations[targetFile.name] = JoinSegmentOperation(
                target = targetFile,
                segmentFiles = files.map { sharedWorkDir.resolve(it) },
                audioFile = audioFile,
            )
        }

        if (separateAudioEncode) {
            val encodedAudioFiles = sharedWorkDir.resolve("audio")
                .listFiles()?.asList() ?: emptyList<File>()

            encodedAudioFiles.filter { it.isFile }
                .filter { !joinSegmentOperations.containsKey(it.name) }
                .forEach { file ->
                    joinSegmentOperations[file.name] = JoinSegmentOperation(
                        target = outputFolder.resolve(file.name),
                        segmentFiles = emptyList(),
                        audioFile = file,
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
            )
        } else {
            log.info { "Moving audio file ${joinSegmentOperation.audioFile} to output folder" }
            val target = joinSegmentOperation.target
            joinSegmentOperation.audioFile!!.copyTo(target, overwrite = true)
            mediaAnalyzerService.analyze(target.absolutePath)
        }
    }
}
