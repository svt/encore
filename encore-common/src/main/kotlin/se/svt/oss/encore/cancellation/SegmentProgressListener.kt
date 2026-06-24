// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.cancellation

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import se.svt.oss.encore.model.SegmentProgressEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger { }

data class SegmentProgressListener(
    val encoreJobId: UUID,
    private val coroutineJob: Job,
    private val totalSegments: Int,
    private val progressChannel: SendChannel<Int>,
) : RedisPubSubAdapter<String, SegmentProgressEvent>() {

    private val completedSegments: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    val anyFailed = AtomicBoolean(false)

    fun completed() = anyFailed.get() || completedSegments.size == totalSegments

    fun completionCount() = completedSegments.size

    override fun smessage(shardChannel: String, event: SegmentProgressEvent) {
        if (event.jobId == encoreJobId) {
            log.debug { "Received $event" }
            if (!event.success) {
                progressChannel.close()
                anyFailed.set(true)
                coroutineJob.cancel("Segment ${event.segment} failed!")
            } else if (completedSegments.add(event.segment)) {
                val percent = (completedSegments.size * 100.0 / totalSegments).toInt()
                progressChannel.trySendBlocking(percent)
                if (percent == 100) {
                    progressChannel.close()
                }
            }
        }
    }
}
