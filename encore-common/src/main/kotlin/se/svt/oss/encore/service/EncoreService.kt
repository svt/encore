// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.time.withTimeout
import mu.KotlinLogging
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.PartialUpdate
import org.springframework.data.redis.core.RedisKeyValueTemplate
import org.springframework.stereotype.Service
import se.svt.oss.encore.cancellation.CancellationListener
import se.svt.oss.encore.cancellation.SegmentProgressListener
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.SegmentProgressEvent
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.process.baseName
import se.svt.oss.encore.process.numSegments
import se.svt.oss.encore.process.segmentDuration
import se.svt.oss.encore.process.segmentLengthOrThrow
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.localencode.LocalEncodeService
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.encore.service.queue.QueueService
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import java.util.Locale
import kotlin.time.TimedValue
import kotlin.time.measureTimedValue

@Service
class EncoreService(
    private val callbackService: CallbackService,
    private val repository: EncoreJobRepository,
    private val ffmpegExecutor: FfmpegExecutor,
    private val redissonClient: RedissonClient,
    private val redisKeyValueTemplate: RedisKeyValueTemplate,
    private val mediaAnalyzerService: MediaAnalyzerService,
    private val localEncodeService: LocalEncodeService,
    private val encoreProperties: EncoreProperties,
    private val queueService: QueueService,
) {

    private val log = KotlinLogging.logger {}

    private val cancelTopicName = "cancel"

    private fun sharedWorkDirOrNull(encoreJob: EncoreJob): File? =
        encoreProperties.sharedWorkDir?.resolve(encoreJob.id.toString())

    private fun sharedWorkDir(encoreJob: EncoreJob): File =
        sharedWorkDirOrNull(encoreJob)
            ?: throw IllegalStateException("Shared work dir has not been configured")

    fun encode(queueItem: QueueItem, job: EncoreJob) {
        when {
            queueItem.segment != null -> encodeSegment(job, queueItem.segment)
            job.segmentLength != null -> encodeSegmented(job)
            else -> encode(job)
        }
    }

    private fun encodeSegmented(encoreJob: EncoreJob) {
        val coroutineJob = Job()
        val cancelListener = CancellationListener(encoreJob.id, coroutineJob)
        var progressListener: SegmentProgressListener? = null
        var cancelTopic: RTopic? = null
        var progressTopic: RTopic? = null
        try {
            initJob(encoreJob)
            val numSegments = encoreJob.numSegments()
            log.debug { "Encoding using $numSegments segments" }
            cancelTopic = redissonClient.getTopic(cancelTopicName)
            cancelTopic.addListener(CancelEvent::class.java, cancelListener)
            progressTopic = redissonClient.getTopic("segment-progress")
            val progressChannel = Channel<Int>()
            progressListener = SegmentProgressListener(encoreJob.id, coroutineJob, numSegments, progressChannel)
            progressTopic.addListener(SegmentProgressEvent::class.java, progressListener)
            val timedOutput = measureTimedValue {
                sharedWorkDir(encoreJob).mkdirs()
                repeat(numSegments) {
                    queueService.enqueue(
                        QueueItem(
                            id = encoreJob.id.toString(),
                            priority = encoreJob.priority,
                            segment = it
                        )
                    )
                }

                runBlocking(coroutineJob + MDCContext()) {
                    withTimeout(encoreProperties.segmentedEncodeTimeout) {
                        handleProgress(progressChannel, encoreJob)
                        while (!progressListener.completed()) {
                            log.info { "Awaiting completion ${progressListener.completionCount()}/$numSegments..." }
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
                    outWorkDir.list()?.filter { it.startsWith(segmentBaseName) }
                        ?.forEach {
                            val suffix = it.replaceFirst(segmentBaseName, "")
                            suffixes.add(suffix)
                            outWorkDir.resolve("$suffix.txt").appendText("file $it\n")
                        }
                }
                val outputFolder = File(encoreJob.outputFolder)
                outputFolder.mkdirs()
                val outputFiles = suffixes.map {
                    val targetName = encoreJob.baseName + it
                    log.info { "Joining segments for $targetName" }
                    val targetFile = outputFolder.resolve(targetName)
                    ffmpegExecutor.joinSegments(outWorkDir.resolve("$it.txt"), targetFile)
                }
                outputFiles
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
            cancelTopic?.removeListener(cancelListener)
            progressListener?.let { progressTopic?.removeListener(it) }
            callbackService.sendProgressCallback(encoreJob)
        }
    }

    private fun encodeSegment(encoreJob: EncoreJob, segmentNumber: Int) {
        try {
            log.info { "Start encoding ${encoreJob.baseName} segment $segmentNumber/${encoreJob.numSegments()} " }
            val outputFolder = sharedWorkDir(encoreJob).absolutePath
            val job = encoreJob.copy(
                baseName = encoreJob.baseName(segmentNumber),
                duration = encoreJob.segmentDuration(segmentNumber),
                inputs = encoreJob.inputs.map {
                    it.withSeekTo((it.seekTo ?: 0.0) + encoreJob.segmentLengthOrThrow() * segmentNumber)
                }
            )
            ffmpegExecutor.run(job, outputFolder, null)
            redissonClient.getTopic("segment-progress").publish(SegmentProgressEvent(encoreJob.id, segmentNumber, true))
            log.info { "Completed ${encoreJob.baseName} segment $segmentNumber/${encoreJob.numSegments()} " }
        } catch (e: Exception) {
            log.error(e) { "Error encoding segment $segmentNumber: ${e.message}" }
            redissonClient.getTopic("segment-progress")
                .publish(SegmentProgressEvent(encoreJob.id, segmentNumber, false))
        }
    }

    private fun encode(encoreJob: EncoreJob) {
        val coroutineJob = Job()
        val cancelListener = CancellationListener(encoreJob.id, coroutineJob)
        var cancelTopic: RTopic? = null
        var outputFolder: String? = null

        try {
            cancelTopic = redissonClient.getTopic(cancelTopicName)
            cancelTopic.addListener(CancelEvent::class.java, cancelListener)
            outputFolder = localEncodeService.outputFolder(encoreJob)

            val timedOutput = measureTimedValue {
                initJob(encoreJob)

                val outputFiles = runBlocking(coroutineJob + MDCContext()) {
                    val progressChannel = Channel<Int>()
                    handleProgress(progressChannel, encoreJob)
                    ffmpegExecutor.run(encoreJob, outputFolder, progressChannel)
                }

                localEncodeService.localEncodedFilesToCorrectDir(outputFolder, outputFiles, encoreJob)
            }

            updateSuccessfulJob(encoreJob, timedOutput)
            log.info { "Done with $encoreJob" }
        } catch (e: InterruptedException) {
            val message = "Job execution interrupted"
            log.error(e) { message }
            encoreJob.status = Status.QUEUED
            encoreJob.message = message
            throw e
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
            cancelTopic?.removeListener(cancelListener)
            callbackService.sendProgressCallback(encoreJob)
            localEncodeService.cleanup(outputFolder)
        }
    }

    @OptIn(FlowPreview::class)
    private fun CoroutineScope.handleProgress(
        progressChannel: ReceiveChannel<Int>,
        encoreJob: EncoreJob
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
        val timeInSeconds = timedOutput.duration.inWholeSeconds
        val speed = outputFiles.filterIsInstance<MediaContainer>().firstOrNull()?.let {
            "%.3f".format(Locale.US, it.duration / timeInSeconds).toDouble()
        } ?: 0.0
        log.info { "Done encoding, time: ${timeInSeconds}s, speed: ${speed}X" }
        encoreJob.output = outputFiles
        encoreJob.status = Status.SUCCESSFUL
        encoreJob.progress = 100
        encoreJob.speed = speed
    }

    private fun initJob(encoreJob: EncoreJob) {
        encoreJob.inputs.forEach { input ->
            mediaAnalyzerService.analyzeInput(input)
        }
        log.info { "Start encoding" }
        encoreJob.status = Status.IN_PROGRESS
        repository.save(encoreJob)
    }
}
