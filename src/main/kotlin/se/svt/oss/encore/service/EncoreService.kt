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
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.localencode.LocalEncodeService
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.encore.service.profile.ProfileService
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.MediaFile
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
    private val mediaAnalyzerService: MediaAnalyzerService,
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

            encoreJob.inputs.forEach { input ->
                mediaAnalyzerService.analyzeInput(input)
            }

            log.info { "Start $encoreJob" }
            encoreJob.status = Status.IN_PROGRESS
            repository.save(encoreJob)

            val profile = profileService.getProfile(encoreJob.profile)

            outputFolder = localEncodeService.outputFolder(encoreJob)

            val outputs = profile.encodes.mapNotNull {
                it.getOutput(
                    encoreJob,
                    encoreProperties.audioMixPresets
                )
            }

            check(outputs.distinctBy { it.id }.size == outputs.size) {
                "Profile ${encoreJob.profile} contains duplicate suffixes: ${outputs.map { it.id }}!"
            }

            val start = System.currentTimeMillis()

            var outputFiles = runBlocking(coroutineJob + MDCContext()) {
                val progressChannel = Channel<Int>()
                handleProgress(progressChannel, encoreJob)
                ffmpegExecutor.run(encoreJob, profile, outputs, outputFolder, progressChannel)
            }

            outputFiles = localEncodeService.localEncodedFilesToCorrectDir(outputFolder, outputFiles, encoreJob)

            val time = (System.currentTimeMillis() - start) / 1000
            val speed = outputFiles.filterIsInstance<MediaContainer>().firstOrNull()?.let {
                "%.3f".format(Locale.US, it.duration / time).toDouble()
            } ?: 0.0
            log.info { "Done encoding, time: ${time}s, speed: ${speed}X" }
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

    private fun updateSuccessfulJob(encoreJob: EncoreJob, output: List<MediaFile>, speed: Double) {
        encoreJob.output = output
        encoreJob.status = Status.SUCCESSFUL
        encoreJob.progress = 100
        encoreJob.speed = speed
    }
}
