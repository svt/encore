// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.lettuce.core.CompareCondition
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import org.springframework.stereotype.Component
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.config.RedisProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.redis.RedisService
import se.svt.oss.encore.service.ApplicationShutdownException
import se.svt.oss.encore.service.queue.QueueUtil.getQueueNumberByPriority
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private val log = KotlinLogging.logger { }

@Component
class QueueService(
    private val encoreProperties: EncoreProperties,
    redisProperties: RedisProperties,
    private val redisService: RedisService,
    private val queueConnection: StatefulRedisConnection<String, QueueItem>,
    private val stringConnection: StatefulRedisConnection<String, String>,
) {
    private val queuePrefix = "${redisProperties.prefix}:queue:"
    private val concurrencyKey = "${redisProperties.prefix}:concurrency"

    fun poll(queueNo: Int, action: (QueueItem, EncoreJob) -> Unit): Boolean {
        val queueItem = if (encoreProperties.pollHigherPrio) {
            pollUntil(queueNo)
        } else {
            pollQueue(queueNo)
        }
        if (queueItem == null) {
            return false
        }

        log.info { "Picked up $queueItem" }
        val id = UUID.fromString(queueItem.id)
        val job = redisService.findByIdOrNull(id)
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
            } catch (e: ApplicationShutdownException) {
                log.warn(e) { "Application was shut down. Will attempt to add back to queue $queueItem" }
                repostJob(queueItem, job)
                return false
            }
        }
        return true
    }

    private fun pollQueue(queueNo: Int) =
        queueConnection.sync()
            .zpopmin("$queuePrefix$queueNo")
            .getValueOrElse(null)

    private fun enqueue(queueNo: Int, item: QueueItem) =
        queueConnection.sync()
            .zadd(
                "$queuePrefix$queueNo",
                (100 - item.priority).toDouble(),
                item,
            )

    private fun pollUntil(queueNo: Int): QueueItem? =
        (0..queueNo)
            .firstNotNullOfOrNull {
                pollQueue(it)?.also { log.debug { "Found item: $it" } }
            }

    private fun retry(id: UUID): EncoreJob? {
        Thread.sleep(2000)
        log.info { "Retrying read of job from repository " }
        return redisService.findByIdOrNull(id)
    }

    private fun repostJob(queueItem: QueueItem, job: EncoreJob) {
        try {
            log.info { "Adding job to queue (repost on interrupt)" }
            enqueue(queueItem)
            if (queueItem.segment == null) {
                job.updateStatus(Status.QUEUED)
                redisService.save(job)
            }
            log.info { "Added job to queue (repost on interrupt)" }
        } catch (e: Exception) {
            if (queueItem.segment == null) {
                val message = "Failed to add interrupted job to queue"
                log.error(e) { message }
                job.updateStatus(Status.FAILED, message)
                redisService.save(job)
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

    fun enqueue(item: QueueItem) = enqueue(getQueueNumberByPriority(item.priority), item)

    fun getQueue(): List<QueueItem> = (0 until encoreProperties.concurrency).flatMap { queueNo ->
        queueConnection.sync().zrange("$queuePrefix$queueNo", 0, -1)
    }

    fun getQueueCount(): Map<String, Long> =
        buildMap {
            var total = 0L
            (0 until encoreProperties.concurrency).forEach { queueNo ->
                val queueName = "$queuePrefix$queueNo"
                val count = queueConnection.sync().zcard(queueName)
                total += count
                put(queueName, count)
            }
            put("total", total)
        }

    private fun getQueueNumberByPriority(priority: Int) =
        getQueueNumberByPriority(encoreProperties.concurrency, priority)

    fun handleOrphanedQueues() {
        try {
            val commands = stringConnection.sync()
            val concurrencyAsString = encoreProperties.concurrency.toString()
            if (commands.set(concurrencyKey, concurrencyAsString, SetArgs().nx()) == "OK") {
                log.debug { "SET $concurrencyKey $concurrencyAsString" }
                return
            }
            val oldConcurrency = commands.get(concurrencyKey).toInt()
            if (oldConcurrency != encoreProperties.concurrency) {
                val setArgs = SetArgs()
                    .compareCondition(CompareCondition.valueNe(concurrencyAsString))

                val resp = commands.set(concurrencyKey, concurrencyAsString, setArgs)

                if (resp == "OK" && oldConcurrency > encoreProperties.concurrency) {
                    log.info { "Moving orphaned queue items to lowest priority queue. Old concurrency: $oldConcurrency, new concurrency: ${encoreProperties.concurrency}" }
                    val lowestPrioQueue = "$queuePrefix${encoreProperties.concurrency - 1}"
                    (encoreProperties.concurrency until oldConcurrency).forEach { queueNo ->
                        val orphaned = commands.zpopmin("$queuePrefix$queueNo", Long.MAX_VALUE)
                        if (orphaned.isNotEmpty()) {
                            commands.zadd(lowestPrioQueue, *orphaned.toTypedArray())
                            log.info { "Moved ${orphaned.size} orphaned queue items from $queuePrefix$queueNo to $lowestPrioQueue" }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Error checking for concurrency change: ${e.message}" }
        }
    }
}
