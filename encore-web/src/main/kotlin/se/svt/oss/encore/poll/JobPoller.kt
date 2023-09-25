// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.poll

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import mu.KotlinLogging
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService
import java.time.Instant
import java.util.concurrent.ScheduledFuture

@Service
class JobPoller(
    private val queueService: QueueService,
    private val encoreService: EncoreService,
    private val scheduler: ThreadPoolTaskScheduler,
    private val encoreProperties: EncoreProperties
) {

    private val log = KotlinLogging.logger {}
    private var scheduledTasks = emptyList<ScheduledFuture<*>>()

    @PostConstruct
    fun init() {
        if (encoreProperties.pollDisabled) {
            return
        }
        val pollQueue = encoreProperties.pollQueue
        scheduledTasks = if (pollQueue != null) {
            listOf(scheduledFuture(pollQueue))
        } else {
            (0 until encoreProperties.concurrency).map { queueNo ->
                scheduledFuture(queueNo)
            }
        }
    }

    private fun scheduledFuture(queueNo: Int): ScheduledFuture<*> =
        scheduler.scheduleWithFixedDelay(
            {
                try {
                    queueService.poll(queueNo, encoreService::encode)
                } catch (e: Throwable) {
                    log.error(e) { "Error polling queue $queueNo!" }
                }
            },
            Instant.now().plus(encoreProperties.pollInitialDelay),
            encoreProperties.pollDelay
        )

    @PreDestroy
    fun destroy() {
        scheduledTasks.forEach { it.cancel(false) }
    }
}
