// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import mu.KotlinLogging
import org.redisson.api.RPriorityBlockingQueue
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.service.queue.QueueUtil.getQueueNumberByPriority
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.TimeUnit
import javax.annotation.PostConstruct

@Component
class QueueService(
    encoreProperties: EncoreProperties,
    private val redisson: RedissonClient
) {

    private val log = KotlinLogging.logger { }
    private val queues = ConcurrentSkipListMap<Int, RPriorityBlockingQueue<QueueItem>>()
    private val concurrency = encoreProperties.concurrency
    private val redisKeyPrefix = encoreProperties.redisKeyPrefix

    fun poll(queueNo: Int): QueueItem? =
        (0..queueNo)
            .asSequence()
            .mapNotNull { getQueue(it).poll() }
            .firstOrNull()

    fun enqueue(job: EncoreJob) {
        val queueItem = QueueItem(
            id = job.id.toString(),
            priority = job.priority,
            created = job.createdDate.toLocalDateTime()
        )
        if (!queueByPrio(job.priority).offer(queueItem, 5, TimeUnit.SECONDS)) {
            throw RuntimeException("Job could not be added to queue!")
        }
    }

    fun getQueue(): List<QueueItem> {
        return (0 until concurrency).flatMap { getQueue(it).toList() }
    }

    private fun queueByPrio(priority: Int) =
        getQueue(getQueueNumberByPriority(concurrency, priority))

    private fun getQueue(queueNo: Int) = queues.computeIfAbsent(queueNo) {
        redisson.getPriorityBlockingQueue("$redisKeyPrefix-queue-$queueNo")
    }

    @PostConstruct
    internal fun handleOrphanedQueues() {
        try {
            val oldConcurrency =
                redisson.getAtomicLong("$redisKeyPrefix-concurrency").getAndSet(concurrency.toLong()).toInt()
            if (oldConcurrency > concurrency) {
                log.info { "Moving orphaned queue items to lowest priority queue. Old concurrency: $oldConcurrency, new concurrency: $concurrency" }
                val lowestPrioQueue = getQueue(concurrency - 1)
                (concurrency until oldConcurrency).forEach { queueNo ->
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
