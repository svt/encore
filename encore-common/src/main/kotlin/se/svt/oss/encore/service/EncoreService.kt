// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.time.withTimeout
import org.springframework.data.redis.core.PartialUpdate
import org.springframework.data.redis.core.RedisKeyValueTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import se.svt.oss.encore.cancellation.CancellationListener
import se.svt.oss.encore.cancellation.SegmentProgressListener
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.RedisEvent
import se.svt.oss.encore.model.SegmentProgressEvent
import se.svt.oss.encore.model.SegmentedEncodingInfo
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.model.queue.Task
import se.svt.oss.encore.model.queue.TaskType
import se.svt.oss.encore.process.baseName
import se.svt.oss.encore.process.numVideoSegments
import se.svt.oss.encore.process.segmentDuration
import se.svt.oss.encore.process.segmentLengthOrThrow
import se.svt.oss.encore.process.segmentedEncodingInfoOrThrow
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.localencode.LocalEncodeService
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.encore.service.queue.QueueService
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import java.nio.file.Paths
import java.util.Locale
import kotlin.io.path.isDirectory
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

private val log = KotlinLogging.logger {}

@Service
class EncoreService(
    private val callbackService: CallbackService,
    private val repository: EncoreJobRepository,
    private val ffmpegExecutor: FfmpegExecutor,
    private val redisMessageListerenerContainer: RedisMessageListenerContainer,
    private val redisKeyValueTemplate: RedisKeyValueTemplate,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: RedisTemplate<String, RedisEvent>,
    private val mediaAnalyzerService: MediaAnalyzerService,
    private val localEncodeService: LocalEncodeService,
    private val encoreProperties: EncoreProperties,
    private val queueService: QueueService,
) {

    private val cancelTopicName = "cancel"

    private fun sharedWorkDirOrNull(encoreJob: EncoreJob): File? =
        encoreProperties.sharedWorkDir?.resolve(encoreJob.id.toString())

    private fun sharedWorkDir(encoreJob: EncoreJob): File =
        sharedWorkDirOrNull(encoreJob)
            ?: throw IllegalStateException("Shared work dir has not been configured")

    fun encode(queueItem: QueueItem, job: EncoreJob) {
        when {
            queueItem.task != null -> encodeSegment(job, queueItem.task)
            job.segmentLength != null -> encodeSegmented(job)
            else -> encode(job)
        }
    }

    private fun encodeSegmented(encoreJob: EncoreJob) {
        val coroutineJob = Job()
        val cancelListener = CancellationListener(objectMapper, encoreJob.id, coroutineJob)
        var progressListener: SegmentProgressListener? = null
        try {
            initJob(encoreJob)

            val segmentedEncodingInfo = encoreJob.segmentedEncodingInfoOrThrow()

            val separateAudioEncode = !segmentedEncodingInfo.segmentedAudioEncode
            val numSegments = segmentedEncodingInfo.numSegments
            val numTasks = segmentedEncodingInfo.numTasks

            log.debug { "Encoding using $numSegments segments" }
            if (separateAudioEncode) {
                log.debug { "Encoding audio separately" }
            }
            redisMessageListerenerContainer.addMessageListener(cancelListener, ChannelTopic.of(cancelTopicName))
            val progressChannel = Channel<Int>()
            progressListener =
                SegmentProgressListener(objectMapper, encoreJob.id, coroutineJob, numTasks, progressChannel)
            redisMessageListerenerContainer.addMessageListener(progressListener, ChannelTopic.of("segment-progress"))
            val timedOutput = measureTimedValue {
                sharedWorkDir(encoreJob).mkdirs()
                var taskNo = 0
                if (separateAudioEncode) {
                    queueService.enqueue(
                        QueueItem(
                            id = encoreJob.id.toString(),
                            priority = encoreJob.priority,
                            task = Task(
                                type = TaskType.AUDIOFULL,
                                taskNo = taskNo++,
                                segment = 0,
                            ),
                        ),
                    )
                }
                val segmentsTaskType = if (separateAudioEncode) {
                    TaskType.VIDEOSEGMENT
                } else {
                    TaskType.AUDIOVIDEOSEGMENT
                }
                repeat(numSegments) {
                    queueService.enqueue(
                        QueueItem(
                            id = encoreJob.id.toString(),
                            priority = encoreJob.priority,
                            task = Task(
                                type = segmentsTaskType,
                                taskNo = taskNo++,
                                segment = it,
                            ),
                        ),
                    )
                }

                runBlocking(coroutineJob + MDCContext()) {
                    withTimeout(encoreProperties.segmentedEncodeTimeout) {
                        handleProgress(progressChannel, encoreJob)
                        progressChannel.trySendBlocking(0)
                        while (!progressListener.completed()) {
                            ShutdownHandler.checkShutdown()
                            log.info { "Awaiting completion ${progressListener.completionCount()}/$numTasks..." }
                            delay(1000)
                        }
                    }
                }
                if (progressListener.anyFailed.get()) {
                    throw RuntimeException("Some segments failed")
                }
                log.info { "All segments completed" }
                val outWorkDir = sharedWorkDir(encoreJob)
                val suffixes = mutableSetOf<String>()
                repeat(numSegments) { segmentNum ->
                    val segmentBaseName = encoreJob.baseName(segmentNum)
                    outWorkDir.list()
                        ?.filter { !Paths.get(it).isDirectory() }
                        ?.filter { it.startsWith(segmentBaseName) }
                        ?.forEach {
                            val suffix = it.replaceFirst(segmentBaseName, "")
                            suffixes.add(suffix)
                            outWorkDir.resolve("$suffix.txt").appendText("file $it\n")
                        }
                }
                val outputFolder = File(encoreJob.outputFolder)
                outputFolder.mkdirs()
                val audioFilesMap: MutableMap<String, File> = if (separateAudioEncode) {
                    sharedWorkDir(encoreJob).resolve("audio")
                        .listFiles()
                        ?.filter { it.isFile }
                        ?.associateBy { it.name }
                        ?.toMutableMap()
                        ?: mutableMapOf()
                } else {
                    mutableMapOf()
                }
                val outputFiles = suffixes.map {
                    val targetName = encoreJob.baseName + it
                    log.info { "Joining segments for $targetName" }
                    val targetFile = outputFolder.resolve(targetName)
                    val audioFile = audioFilesMap.remove(targetName)
                    ffmpegExecutor.joinSegments(encoreJob, outWorkDir.resolve("$it.txt"), targetFile, audioFile)
                }
                val audioOnlyOutputFiles = if (separateAudioEncode) {
                    audioFilesMap.values.map {
                        log.info { "Moving audio file ${it.name} to output folder" }
                        val target = outputFolder.resolve(it.name)
                        it.copyTo(target, overwrite = true)
                        mediaAnalyzerService.analyze(target.absolutePath)
                    }
                } else {
                    emptyList()
                }
                outputFiles + audioOnlyOutputFiles
            }
            updateSuccessfulJob(encoreJob, timedOutput)
        } catch (e: CancellationException) {
            log.error(e) { "Job execution cancelled: ${e.message}" }
            encoreJob.status = Status.CANCELLED
            encoreJob.message = e.message
        } catch (e: Exception) {
            log.error(e) { "Job execution failed: ${e.message}" }
            encoreJob.status = Status.FAILED
            encoreJob.message = e.message
        } finally {
            repository.save(encoreJob)
            sharedWorkDirOrNull(encoreJob)?.deleteRecursively()
            redisMessageListerenerContainer.removeMessageListener(cancelListener)
            progressListener?.let { redisMessageListerenerContainer.removeMessageListener(it) }
            callbackService.sendProgressCallback(encoreJob)
        }
    }

    private fun encodeSegment(encoreJob: EncoreJob, task: Task) {
        val taskNo = task.taskNo
        try {
            log.info { "Start encoding ${encoreJob.baseName} task $taskNo/${encoreJob.segmentedEncodingInfo?.numTasks} (${task.type})" }
            val encodingMode = when (task.type) {
                TaskType.AUDIOFULL -> EncodingMode.AUDIO_ONLY
                TaskType.VIDEOSEGMENT -> EncodingMode.VIDEO_ONLY
                TaskType.AUDIOVIDEOSEGMENT -> EncodingMode.AUDIO_AND_VIDEO
            }
            val (job, outputFolder) = if (encodingMode == EncodingMode.AUDIO_ONLY) {
                Pair(encoreJob, sharedWorkDir(encoreJob).resolve("audio").absolutePath)
            } else {
                val segmentNumber = task.segment
                val job = encoreJob.copy(
                    baseName = encoreJob.baseName(segmentNumber),
                    duration = encoreJob.segmentDuration(segmentNumber),
                    inputs = encoreJob.inputs.map {
                        it.withSeekTo((it.seekTo ?: 0.0) + encoreJob.segmentLengthOrThrow() * segmentNumber)
                    },
                )
                Pair(job, sharedWorkDir(encoreJob).absolutePath)
            }
            ffmpegExecutor.run(job, outputFolder, null, encodingMode)
            redisTemplate.convertAndSend("segment-progress", SegmentProgressEvent(encoreJob.id, taskNo, true))
            log.info { "Completed ${encoreJob.baseName} task $taskNo/${encoreJob.segmentedEncodingInfo?.numTasks} " }
        } catch (e: ApplicationShutdownException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Error encoding task $taskNo: ${e.message}" }
            redisTemplate.convertAndSend("segment-progress", SegmentProgressEvent(encoreJob.id, taskNo, false))
        }
    }

    private fun encode(encoreJob: EncoreJob) {
        val coroutineJob = Job()
        val cancelListener = CancellationListener(objectMapper, encoreJob.id, coroutineJob)
        var outputFolder: String? = null

        try {
            redisMessageListerenerContainer.addMessageListener(cancelListener, ChannelTopic.of(cancelTopicName))
            outputFolder = localEncodeService.outputFolder(encoreJob)

            val timedOutput = measureTimedValue {
                initJob(encoreJob)

                val outputFiles = runBlocking(coroutineJob + MDCContext()) {
                    val progressChannel = Channel<Int>()
                    handleProgress(progressChannel, encoreJob)
                    progressChannel.trySendBlocking(0)
                    ffmpegExecutor.run(encoreJob, outputFolder, progressChannel)
                }

                localEncodeService.localEncodedFilesToCorrectDir(outputFolder, outputFiles, encoreJob)
            }

            updateSuccessfulJob(encoreJob, timedOutput)
            log.info { "Done with $encoreJob" }
            repository.save(encoreJob)
            callbackService.sendProgressCallback(encoreJob)
        } catch (e: ApplicationShutdownException) {
            throw e
        } catch (e: Exception) {
            log.error(e) { "Job execution failed: ${e.message}" }
            encoreJob.status = if (e is CancellationException) {
                Status.CANCELLED
            } else {
                Status.FAILED
            }
            encoreJob.message = e.message
            repository.save(encoreJob)
            callbackService.sendProgressCallback(encoreJob)
        } finally {
            redisMessageListerenerContainer.removeMessageListener(cancelListener)
            localEncodeService.cleanup(outputFolder)
        }
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.handleProgress(
        progressChannel: ReceiveChannel<Int>,
        encoreJob: EncoreJob,
    ) {
        launch {
            progressChannel.consumeAsFlow()
                .conflate()
                .distinctUntilChanged()
                .sample(10_000)
                .collect {
                    log.info { "Received progress $it" }
                    try {
                        encoreJob.progress = it
                        val partialUpdate = PartialUpdate(encoreJob.id, EncoreJob::class.java)
                            .set(encoreJob::progress.name, encoreJob.progress)
                        redisKeyValueTemplate.update(partialUpdate)
                        callbackService.sendProgressCallback(encoreJob)
                    } catch (e: Exception) {
                        log.warn(e) { "Error updating progress!" }
                    }
                }
        }
    }

    private fun updateSuccessfulJob(encoreJob: EncoreJob, timedOutput: TimedValue<List<MediaFile>>) {
        val outputFiles = timedOutput.value
        val timeInMilliSeconds = timedOutput.duration.inWholeMilliseconds
        val speed = outputFiles.filterIsInstance<MediaContainer>().firstOrNull()?.let {
            "%.3f".format(Locale.US, it.duration * 1000 / timeInMilliSeconds).toDouble()
        } ?: 0.0
        log.info { "Done encoding, time: ${timedOutput.duration.inWholeSeconds}s, speed: ${speed}X" }
        encoreJob.output = outputFiles
        encoreJob.status = Status.SUCCESSFUL
        encoreJob.progress = 100
        encoreJob.speed = speed
    }

    private fun initJob(encoreJob: EncoreJob) {
        encoreJob.inputs.forEach { input ->
            mediaAnalyzerService.analyzeInput(input)
        }
        if (encoreJob.segmentLength != null) {
            val segmentedAudioEncode: Boolean = encoreJob.segmentedEncodingEnabledForAudio
                ?: encoreProperties.encoding.segmentedEncoding.enabledForAudio
            val numSegments = encoreJob.numVideoSegments()
            val numTasks = numSegments + if (segmentedAudioEncode) 0 else 1
            encoreJob.segmentedEncodingInfo = SegmentedEncodingInfo(
                segmentLength = encoreJob.segmentLengthOrThrow(),
                segmentedAudioEncode = segmentedAudioEncode,
                numTasks = numTasks,
                numSegments = numSegments,
            )
        }
        log.info { "Start encoding" }
        encoreJob.status = Status.IN_PROGRESS
        repository.save(encoreJob)
    }
}
