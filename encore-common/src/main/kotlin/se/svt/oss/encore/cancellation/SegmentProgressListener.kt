// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.cancellation

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import org.redisson.api.listener.MessageListener
import se.svt.oss.encore.model.SegmentProgressEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class SegmentProgressListener(
    private val encoreJobId: UUID,
    private val coroutineJob: Job,
    private val totalSegments: Int,
    private val progressChannel: SendChannel<Int>
) : MessageListener<SegmentProgressEvent> {

    private val completedSegments: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    val anyFailed = AtomicBoolean(false)

    fun completed() = anyFailed.get() || completedSegments.size == totalSegments

    fun completionCount() = completedSegments.size

    override fun onMessage(channel: CharSequence?, msg: SegmentProgressEvent?) {
        if (msg?.jobId == encoreJobId) {
            if (!msg.success) {
                progressChannel.close()
                anyFailed.set(true)
                coroutineJob.cancel("Segment ${msg.segment} failed!")
            } else if (completedSegments.add(msg.segment)) {
                val percent = (completedSegments.size * 100.0 / totalSegments).toInt()
                progressChannel.trySendBlocking(percent)
                if (percent == 100) {
                    progressChannel.close()
                }
            }
        }
    }
}
