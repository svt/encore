// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import mu.withLoggingContext
import org.redisson.api.RPriorityBlockingQueue
import org.redisson.api.RedissonClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.queue.QueueUtil.getQueueNumberByPriority
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.TimeUnit

@Component
class QueueService(
    private val encoreProperties: EncoreProperties,
    private val redisson: RedissonClient,
    private val repository: EncoreJobRepository,
) {

    private val log = KotlinLogging.logger { }
    private val queues = ConcurrentSkipListMap<Int, RPriorityBlockingQueue<QueueItem>>()

    fun poll(queueNo: Int, action: (QueueItem, EncoreJob) -> Unit): Boolean {
        val queueItem = if (encoreProperties.pollHigherPrio) {
            pollUntil(queueNo)
        } else {
            getQueue(queueNo).poll()
        }
        if (queueItem == null) {
            return false
        }
        log.info { "Picked up $queueItem" }
        val id = UUID.fromString(queueItem.id)
        val job = repository.findByIdOrNull(id)
            ?: retry(id) // Sometimes there has been sync issues
            ?: throw RuntimeException("Job ${queueItem.id} does not exist")
        if (job.status.isCancelled) {
            log.info { "Job was cancelled" }
            return true
        }
        if (queueItem.segment != null && job.status == Status.FAILED) {
            log.info { "Main job has failed" }
            return true
        }
        withLoggingContext(job.contextMap) {
            try {
                action.invoke(queueItem, job)
            } catch (e: InterruptedException) {
                repostJob(queueItem, job)
            }
        }
        return true
    }

    private fun pollUntil(queueNo: Int): QueueItem? =
        (0..queueNo)
            .asSequence()
            .mapNotNull { getQueue(it).poll() }
            .firstOrNull()

    private fun retry(id: UUID): EncoreJob? {
        Thread.sleep(5000)
        log.info { "Retrying read of job from repository " }
        return repository.findByIdOrNull(id)
    }

    private fun repostJob(queueItem: QueueItem, job: EncoreJob) {
        try {
            log.info { "Adding job to queue (repost on interrupt)" }
            enqueue(queueItem)
            if (queueItem.segment == null) {
                job.status = Status.QUEUED
                repository.save(job)
            }
            log.info { "Added job to queue (repost on interrupt)" }
        } catch (e: Exception) {
            if (queueItem.segment == null) {
                val message = "Failed to add interrupted job to queue"
                log.error(e) { message }
                job.message = message
                job.status = Status.FAILED
                repository.save(job)
            }
        }
    }

    fun enqueue(job: EncoreJob) {
        val queueItem = QueueItem(
            id = job.id.toString(),
            priority = job.priority,
            created = job.createdDate.toLocalDateTime()
        )
        enqueue(queueItem)
    }

    fun enqueue(item: QueueItem) {
        if (!queueByPrio(item.priority).offer(item, 5, TimeUnit.SECONDS)) {
            throw RuntimeException("Job could not be added to queue!")
        }
    }

    fun getQueue(): List<QueueItem> {
        return (0 until encoreProperties.concurrency).flatMap { getQueue(it).toList() }
    }

    private fun queueByPrio(priority: Int) =
        getQueue(getQueueNumberByPriority(encoreProperties.concurrency, priority))

    private fun getQueue(queueNo: Int) = queues.computeIfAbsent(queueNo) {
        redisson.getPriorityBlockingQueue("${encoreProperties.redisKeyPrefix}-queue-$queueNo")
    }

    @PostConstruct
    internal fun handleOrphanedQueues() {
        try {
            val oldConcurrency =
                redisson.getAtomicLong("${encoreProperties.redisKeyPrefix}-concurrency").getAndSet(encoreProperties.concurrency.toLong()).toInt()
            if (oldConcurrency > encoreProperties.concurrency) {
                log.info { "Moving orphaned queue items to lowest priority queue. Old concurrency: $oldConcurrency, new concurrency: ${encoreProperties.concurrency}" }
                val lowestPrioQueue = getQueue(encoreProperties.concurrency - 1)
                (encoreProperties.concurrency until oldConcurrency).forEach { queueNo ->
                    val orphanedQueue = getQueue(queueNo)
                    val transferred = orphanedQueue.drainTo(lowestPrioQueue)
                    log.info { "Moved $transferred orphaned items from queue $queueNo to lowest priority queue." }
                    orphanedQueue.delete()
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Error checking for concurrency change: ${e.message}" }
        }
    }
}
