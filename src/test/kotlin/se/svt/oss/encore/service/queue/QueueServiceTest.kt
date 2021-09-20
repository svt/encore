// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.queue

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.api.RPriorityBlockingQueue
import org.redisson.api.RedissonClient
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.queue.QueueItem
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
internal class QueueServiceTest {
    private val highPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()
    private val standardPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()
    private val lowPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()

    private val encoreProperties = EncoreProperties(concurrency = 3,)

    @MockK
    private lateinit var redisson: RedissonClient

    @InjectMockKs
    private lateinit var queueService: QueueService

    private val queueItemHighPrio = QueueItem("high", 90)
    private val queueItemStandardPrio = QueueItem("standard", 51)
    private val queueItemLowPrio = QueueItem("low", 10)

    @BeforeEach
    internal fun setUp() {
        every { highPriorityQueue.poll() } returns queueItemHighPrio
        every { standardPriorityQueue.poll() } returns queueItemStandardPrio
        every { lowPriorityQueue.poll() } returns queueItemLowPrio
        every { redisson.getPriorityBlockingQueue<QueueItem>("${encoreProperties.redisKeyPrefix}-queue-0") } returns highPriorityQueue
        every { redisson.getPriorityBlockingQueue<QueueItem>("${encoreProperties.redisKeyPrefix}-queue-1") } returns standardPriorityQueue
        every { redisson.getPriorityBlockingQueue<QueueItem>("${encoreProperties.redisKeyPrefix}-queue-2") } returns lowPriorityQueue
    }

    @Nested
    inner class Init {

        @Test
        fun concurrencyReduced() {
            val orphanedQueue1 = mockk<RPriorityBlockingQueue<QueueItem>>()
            val orphanedQueue2 = mockk<RPriorityBlockingQueue<QueueItem>>()
            every { orphanedQueue1.drainTo(any()) } returns 1
            every { orphanedQueue2.drainTo(any()) } returns 1
            every { orphanedQueue1.delete() } returns true
            every { orphanedQueue2.delete() } returns true
            val concurrency = encoreProperties.concurrency
            every { redisson.getAtomicLong("${encoreProperties.redisKeyPrefix}-concurrency").getAndSet(concurrency.toLong()) } returns concurrency.toLong() + 2
            every { redisson.getPriorityBlockingQueue<QueueItem>("${encoreProperties.redisKeyPrefix}-queue-$concurrency") } returns orphanedQueue1
            every { redisson.getPriorityBlockingQueue<QueueItem>("${encoreProperties.redisKeyPrefix}-queue-${concurrency + 1}") } returns orphanedQueue2

            queueService.handleOrphanedQueues()

            verify { orphanedQueue1.drainTo(lowPriorityQueue) }
            verify { orphanedQueue2.drainTo(lowPriorityQueue) }
            verify { orphanedQueue1.delete() }
            verify { orphanedQueue2.delete() }
        }
    }

    @Nested
    inner class PollHighPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.poll() }
            verify(exactly = 0) { standardPriorityQueue.poll() }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(0)).isSameAs(queueItemHighPrio)
        }

        @Test
        fun `returns null if high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            assertThat(queueService.poll(0)).isNull()
        }
    }

    @Nested
    inner class PollHighOrStandardPriorityQueue {

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(1)).isSameAs(queueItemHighPrio)
            verify { highPriorityQueue.poll() }
            verify(exactly = 0) { standardPriorityQueue.poll() }
        }

        @Test
        fun `returns items from standard priority queue if high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            assertThat(queueService.poll(1)).isSameAs(queueItemStandardPrio)
            verify { highPriorityQueue.poll() }
            verify { standardPriorityQueue.poll() }
        }

        @Test
        fun `returns null if both queues empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            assertThat(queueService.poll(1)).isNull()
            verify { highPriorityQueue.poll() }
            verify { standardPriorityQueue.poll() }
        }
    }

    @Nested
    inner class PollHighOrLowPriorityQueue {

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(2)).isSameAs(queueItemHighPrio)
            verify { highPriorityQueue.poll() }
            verify(exactly = 0) { lowPriorityQueue.poll() }
        }

        @Test
        fun `returns items from low priority queue if present and high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            assertThat(queueService.poll(2)).isSameAs(queueItemLowPrio)
            verify { highPriorityQueue.poll() }
            verify { lowPriorityQueue.poll() }
        }

        @Test
        fun `returns null if both queues are empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            every { lowPriorityQueue.poll() } returns null
            assertThat(queueService.poll(2)).isNull()
            verify { highPriorityQueue.poll() }
            verify { lowPriorityQueue.poll() }
        }
    }

    @Nested
    inner class Enqueue {

        @Test
        fun `low priority job is enqueued on low priority queue`() {
            val job = defaultEncoreJob(10)
            every { lowPriorityQueue.offer(any(), any(), any()) } returns true
            queueService.enqueue(job)
            verify { lowPriorityQueue.offer(expectedQueueItem(job), 5, TimeUnit.SECONDS) }
            verify(exactly = 0) { standardPriorityQueue.offer(any(), any(), any()) }
            verify(exactly = 0) { highPriorityQueue.offer(any(), any(), any()) }
        }

        @Test
        fun `standard priority job is enqueued on standard priority queue`() {
            val job = defaultEncoreJob(55)
            every { standardPriorityQueue.offer(any(), any(), any()) } returns true
            queueService.enqueue(job)
            verify { standardPriorityQueue.offer(expectedQueueItem(job), 5, TimeUnit.SECONDS) }
            verify(exactly = 0) { highPriorityQueue.offer(any(), any(), any()) }
            verify(exactly = 0) { lowPriorityQueue.offer(any(), any(), any()) }
        }

        @Test
        fun `high priority job is enqueued on high priority queue`() {
            val job = defaultEncoreJob(priority = 90)
            every { highPriorityQueue.offer(any(), any(), any()) } returns true
            queueService.enqueue(job)
            verify { highPriorityQueue.offer(expectedQueueItem(job), 5, TimeUnit.SECONDS) }
            verify(exactly = 0) { standardPriorityQueue.offer(any(), any(), any()) }
            verify(exactly = 0) { lowPriorityQueue.offer(any(), any(), any()) }
        }

        private fun expectedQueueItem(job: EncoreJob) =
            QueueItem(
                id = job.id.toString(),
                priority = job.priority,
                created = job.createdDate.toLocalDateTime()
            )
    }
}
