// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.PartialUpdate
import org.springframework.data.redis.core.RedisKeyValueTemplate
import org.springframework.stereotype.Service
import se.svt.oss.encore.cancellation.CancellationListener
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.mediafile.trimAudio
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.localencode.LocalEncodeService
import se.svt.oss.encore.service.profile.ProfileService
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.MediaFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.util.Locale

@Service
@ExperimentalCoroutinesApi
@FlowPreview
class EncoreService(
    private val callbackService: CallbackService,
    private val repository: EncoreJobRepository,
    private val profileService: ProfileService,
    private val ffmpegExecutor: FfmpegExecutor,
    private val redissonClient: RedissonClient,
    private val redisKeyValueTemplate: RedisKeyValueTemplate,
    private val mediaAnalyzer: MediaAnalyzer,
    private val localEncodeService: LocalEncodeService,
    private val encoreProperties: EncoreProperties
) {

    private val log = KotlinLogging.logger {}

    private val cancelTopicName = "cancel"

    fun encode(encoreJob: EncoreJob) {
        val coroutineJob = Job()
        val cancelListener = CancellationListener(encoreJob.id, coroutineJob)
        var cancelTopic: RTopic? = null
        var outputFolder: String? = null

        try {
            cancelTopic = redissonClient.getTopic(cancelTopicName)
            cancelTopic.addListener(CancelEvent::class.java, cancelListener)

            log.debug { "Analyzing file $encoreJob" }
            var videoFile = mediaAnalyzer.analyze(encoreJob.filename, true) as? VideoFile
                ?: throw RuntimeException("${encoreJob.filename} is not a video file!")

            videoFile = videoFile.trimAudio(encoreJob.useFirstAudioStreams)
            encoreJob.input = videoFile

            log.info { "Start $encoreJob" }
            encoreJob.status = Status.IN_PROGRESS
            repository.save(encoreJob)

            val profile = profileService.getProfile(encoreJob.profile)

            outputFolder = localEncodeService.outputFolder(encoreJob)

            val outputs = profile.encodes.mapNotNull {
                it.getOutput(
                    videoFile,
                    outputFolder,
                    encoreJob.debugOverlay,
                    encoreJob.thumbnailTime,
                    encoreProperties.audioMixPresets
                )
            }

            val start = System.currentTimeMillis()

            var outputFiles = runBlocking(coroutineJob + MDCContext()) {
                val progressChannel = Channel<Int>()
                handleProgress(progressChannel, encoreJob)
                ffmpegExecutor.run(encoreJob, profile, outputs, progressChannel)
            }

            outputFiles = localEncodeService.localEncodedFilesToCorrectDir(outputFolder, outputFiles, encoreJob)

            val time = (System.currentTimeMillis() - start) / 1000
            val speed = "%.3f".format(Locale.US, videoFile.duration / time).toDouble()
            log.info { "DONE ENCODING time: ${time}s, speed: ${speed}X" }
            updateSuccessfulJob(encoreJob, outputFiles, speed)
            log.info { "Done with $encoreJob" }
        } catch (e: InterruptedException) {
            val message = "Job execution interrupted"
            log.error(e) { message }
            encoreJob.status = Status.QUEUED
            encoreJob.message = message
            throw e
        } catch (e: CancellationException) {
            log.error(e) { "Job execution cancelled: $e.message" }
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
                    log.info { "RECEIVED PROGRESS $it" }
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

    private fun updateSuccessfulJob(encoreJob: EncoreJob, output: List<MediaFile>, speed: Double) {
        encoreJob.output = output
        encoreJob.status = Status.SUCCESSFUL
        encoreJob.progress = 100
        encoreJob.speed = speed
    }
}
