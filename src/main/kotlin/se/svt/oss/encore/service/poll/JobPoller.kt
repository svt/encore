// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.poll

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import mu.KotlinLogging
import mu.withLoggingContext
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ScheduledFuture
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Service
@ExperimentalCoroutinesApi
@FlowPreview
class JobPoller(
    private val repository: EncoreJobRepository,
    private val queueService: QueueService,
    private val encoreService: EncoreService,
    private val scheduler: ThreadPoolTaskScheduler,
    private val encoreProperties: EncoreProperties,
) {

    private val log = KotlinLogging.logger {}
    private var scheduledTasks = emptyList<ScheduledFuture<*>>()

    @PostConstruct
    fun init() {
        scheduledTasks = (0 until encoreProperties.concurrency).map { queueNo ->
            scheduler.scheduleWithFixedDelay(
                {
                    try {
                        queueService.poll(queueNo)?.let { handleJob(it) }
                    } catch (e: Throwable) {
                        log.error(e) { "Error polling queue $queueNo!" }
                    }
                },
                Instant.now().plus(encoreProperties.pollInitialDelay),
                encoreProperties.pollDelay
            )
        }
    }

    @PreDestroy
    fun destroy() {
        scheduledTasks.forEach { it.cancel(false) }
    }

    private fun handleJob(queueItem: QueueItem) {
        val id = UUID.fromString(queueItem.id)
        log.info { "Handling job $id" }
        val job = repository.findByIdOrNull(id)
            ?: retry(id) // Sometimes there has been sync issues
            ?: throw RuntimeException("Job ${queueItem.id} does not exist")

        withLoggingContext(job.contextMap) {
            if (job.status.isCancelled) {
                log.info { "Job was cancelled" }
                return
            }
            log.info { "Running job" }
            try {
                encoreService.encode(job)
            } catch (e: InterruptedException) {
                repostJob(job)
            }
        }
    }

    private fun repostJob(job: EncoreJob) {
        try {
            log.info { "Adding job to queue (repost on interrupt)" }
            queueService.enqueue(job)
            log.info { "Added job to queue (repost on interrupt)" }
        } catch (e: Exception) {
            val message = "Failed to add interrupted job to queue"
            log.error(e) { message }
            job.message = message
            job.status = Status.FAILED
            repository.save(job)
        }
    }

    private fun retry(id: UUID): EncoreJob? {
        Thread.sleep(5000)
        log.info { "Retrying read of job from repository " }
        return repository.findByIdOrNull(id)
    }
}
