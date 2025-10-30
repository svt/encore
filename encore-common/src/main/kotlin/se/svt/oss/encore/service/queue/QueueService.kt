// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import org.springframework.data.redis.core.BoundZSetOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.support.atomic.RedisAtomicInteger
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.ApplicationShutdownException
import se.svt.oss.encore.service.queue.QueueUtil.getQueueNumberByPriority
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.max
import kotlin.math.min

private val log = KotlinLogging.logger { }

@Component
class QueueService(
    private val encoreProperties: EncoreProperties,
    private val redisTemplate: RedisTemplate<String, QueueItem>,
    private val repository: EncoreJobRepository,
    private val redisQueueMigrationScript: RedisScript<Boolean>,
) {
    private val queues = ConcurrentSkipListMap<Int, BoundZSetOperations<String, QueueItem>>()

    fun poll(queueNo: Int, action: (QueueItem, EncoreJob) -> Unit): Boolean {
        val queueItem = if (encoreProperties.pollHigherPrio) {
            pollUntil(queueNo)
        } else {
            getQueue(queueNo).popMin()?.value
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
        if (queueItem.task != null && job.status == Status.FAILED) {
            log.info { "Main job has failed" }
            return true
        }
        withLoggingContext(job.contextMap) {
            try {
                action.invoke(queueItem, job)
            } catch (e: ApplicationShutdownException) {
                log.warn(e) { "Application was shut down. Will attempt to add back to queue $queueItem" }
                repostJob(queueItem, job)
                return false
            }
        }
        return true
    }

    private fun pollUntil(queueNo: Int): QueueItem? =
        (0..queueNo)
            .asSequence()
            .mapNotNull {
                val queue = getQueue(it)
                queue.popMin()?.value?.also {
                    log.debug { "Found item in ${queue.key}: $it" }
                }
            }
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
            if (queueItem.task == null) {
                job.status = Status.QUEUED
                repository.save(job)
            }
            log.info { "Added job to queue (repost on interrupt)" }
        } catch (e: Exception) {
            if (queueItem.task == null) {
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
            priority = max(min(job.priority, 100), 0),
            created = job.createdDate.toLocalDateTime(),
        )
        enqueue(queueItem)
    }

    fun enqueue(item: QueueItem) {
        if (!queueByPrio(item.priority).add(item, (100 - item.priority).toDouble())!!) {
            throw RuntimeException("Job could not be added to queue!")
        }
    }

    fun getQueue(): List<QueueItem> = (0 until encoreProperties.concurrency).flatMap { queueNo ->
        getQueue(queueNo).scan(ScanOptions.NONE).use { cursor ->
            cursor.asSequence().mapNotNull { it.value }.toList()
        }
    }

    private fun queueByPrio(priority: Int) =
        getQueue(getQueueNumberByPriority(encoreProperties.concurrency, priority))

    private fun getQueue(queueNo: Int) = queues.computeIfAbsent(queueNo) {
        redisTemplate.boundZSetOps(getQueueKey(queueNo))
    }

    private fun getQueueKey(queueNo: Int) = "${encoreProperties.redisKeyPrefix}-queue-$queueNo"

    fun migrateQueues() {
        if (encoreProperties.queueMigrationScriptEnabled) {
            val concurrency =
                RedisAtomicInteger("${encoreProperties.redisKeyPrefix}-concurrency", redisTemplate.connectionFactory!!)
                    .get()
            (0 until concurrency).forEach {
                val migrated = redisTemplate.execute(redisQueueMigrationScript, listOf(getQueueKey(it)))
                if (migrated) {
                    log.info { "Migrated queue ${getQueueKey(it)} from list to zset" }
                }
            }
        } else {
            log.debug { "Queue migration is disabled" }
        }
    }

    fun handleOrphanedQueues() {
        try {
            val oldConcurrency =
                RedisAtomicInteger("${encoreProperties.redisKeyPrefix}-concurrency", redisTemplate.connectionFactory!!)
                    .getAndSet(encoreProperties.concurrency)
            if (oldConcurrency > encoreProperties.concurrency) {
                log.info { "Moving orphaned queue items to lowest priority queue. Old concurrency: $oldConcurrency, new concurrency: ${encoreProperties.concurrency}" }
                val lowestPrioQueue = getQueue(encoreProperties.concurrency - 1)
                (encoreProperties.concurrency until oldConcurrency).forEach { queueNo ->
                    val orphanedQueue = getQueue(queueNo)
                    orphanedQueue.popMin(Long.MAX_VALUE)?.let {
                        lowestPrioQueue.add(it)
                        log.info { "Moved ${it.size} orphaned items from queue $queueNo to lowest priority queue." }
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Error checking for concurrency change: ${e.message}" }
        }
    }
}
