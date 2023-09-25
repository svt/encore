// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.queue

import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.redisson.api.RPriorityBlockingQueue
import org.redisson.api.RedissonClient
import org.springframework.data.repository.findByIdOrNull
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockKExtension::class)
internal class QueueServiceTest {
    private val highPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()
    private val standardPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()
    private val lowPriorityQueue = mockk<RPriorityBlockingQueue<QueueItem>>()

    val keyPrefix = "encore"
    private val encoreProperties = mockk<EncoreProperties> {
        every { concurrency } returns 3
        every { redisKeyPrefix } returns keyPrefix
        every { pollHigherPrio } returns true
    }

    @MockK
    private lateinit var redisson: RedissonClient

    @MockK
    private lateinit var repository: EncoreJobRepository

    @InjectMockKs
    private lateinit var queueService: QueueService

    private val queueItemHighPrio = QueueItem(UUID.randomUUID().toString(), 90)
    private val queueItemStandardPrio = QueueItem(UUID.randomUUID().toString(), 51)
    private val queueItemLowPrio = QueueItem(UUID.randomUUID().toString(), 10)
    private val highPrioJob = mockk<EncoreJob> {
        every { contextMap } returns emptyMap()
        every { status } returns Status.QUEUED
    }
    private val standardPrioJob = mockk<EncoreJob> {
        every { contextMap } returns emptyMap()
        every { status } returns Status.QUEUED
        every { status = any() } just Runs
        every { message = any() } just Runs
    }
    private val lowPrioJob = mockk<EncoreJob> {
        every { status } returns Status.QUEUED
        every { contextMap } returns emptyMap()
    }

    private fun mockLambda(expectedQueueItem: QueueItem, expectedEncoreJob: EncoreJob): (QueueItem, EncoreJob) -> Unit =
        { item, job ->
            assertThat(item).isSameAs(expectedQueueItem)
            assertThat(job).isSameAs(expectedEncoreJob)
        }

    private val expectHighPrio = mockLambda(queueItemHighPrio, highPrioJob)
    private val expectStandardPrio = mockLambda(queueItemStandardPrio, standardPrioJob)
    private val expectLowPrio = mockLambda(queueItemLowPrio, lowPrioJob)
    private val expectNone: (QueueItem, EncoreJob) -> Unit = { queueItem, _ ->
        throw RuntimeException("Unexpected call with $queueItem")
    }

    @BeforeEach
    internal fun setUp() {
        every { highPriorityQueue.poll() } returns queueItemHighPrio
        every { standardPriorityQueue.poll() } returns queueItemStandardPrio
        every { lowPriorityQueue.poll() } returns queueItemLowPrio
        every { repository.findByIdOrNull(UUID.fromString(queueItemHighPrio.id)) } returns highPrioJob
        every { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) } returns standardPrioJob
        every { repository.findByIdOrNull(UUID.fromString(queueItemLowPrio.id)) } returns lowPrioJob
        every { redisson.getPriorityBlockingQueue<QueueItem>("$keyPrefix-queue-0") } returns highPriorityQueue
        every { redisson.getPriorityBlockingQueue<QueueItem>("$keyPrefix-queue-1") } returns standardPriorityQueue
        every { redisson.getPriorityBlockingQueue<QueueItem>("$keyPrefix-queue-2") } returns lowPriorityQueue
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
            every {
                redisson.getAtomicLong("$keyPrefix-concurrency").getAndSet(concurrency.toLong())
            } returns concurrency.toLong() + 2
            every { redisson.getPriorityBlockingQueue<QueueItem>("$keyPrefix-queue-$concurrency") } returns orphanedQueue1
            every { redisson.getPriorityBlockingQueue<QueueItem>("$keyPrefix-queue-${concurrency + 1}") } returns orphanedQueue2

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
            verify { standardPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(0, expectHighPrio)).isTrue
        }

        @Test
        fun `returns null if high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            assertThat(queueService.poll(0, expectNone)).isFalse
        }
    }

    @Nested
    inner class PollHighOrStandardPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.poll() }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(1, expectHighPrio)).isTrue
            verify { standardPriorityQueue wasNot Called }
        }

        @Test
        fun `returns items from standard priority queue if high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            assertThat(queueService.poll(1, expectStandardPrio)).isTrue
            verify { standardPriorityQueue.poll() }
        }

        @Test
        fun `returns null if both queues empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            assertThat(queueService.poll(1, expectNone)).isFalse
            verify { standardPriorityQueue.poll() }
        }
    }

    @Nested
    inner class PollStandardPriorityQueue {

        @BeforeEach
        fun setUp() {
            every { encoreProperties.pollHigherPrio } returns false
        }

        @AfterEach
        fun tearDown() {
            verify { standardPriorityQueue.poll() }
            verify { highPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from queue`() {
            assertThat(queueService.poll(1, expectStandardPrio)).isTrue
        }

        @Test
        fun `empty queue`() {
            every { standardPriorityQueue.poll() } returns null
            assertThat(queueService.poll(1, expectNone)).isFalse
        }

        @Test
        fun `job not synced yet is retried`() {
            every { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) } returns null andThen standardPrioJob
            assertThat(queueService.poll(1, expectStandardPrio)).isTrue
            verify(exactly = 2) { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) }
        }

        @Test
        fun `non-existing job throws`() {
            every { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) } returns null
            assertThatThrownBy { queueService.poll(1, expectStandardPrio) }
                .hasMessageEndingWith("does not exist")
            verify(exactly = 2) { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) }
        }

        @Test
        fun `reenqueue on interrupt`() {
            every { standardPriorityQueue.offer(any(), any(), any()) } returns true
            every { repository.save(any()) } answers { firstArg() }
            queueService.poll(1) { _, _ -> throw InterruptedException("shut down") }
            verify { standardPriorityQueue.offer(queueItemStandardPrio, 5, TimeUnit.SECONDS) }
            verify { standardPrioJob.status = Status.QUEUED }
            verify { repository.save(standardPrioJob) }
        }

        @Test
        fun `reenqueue on interrupt fails`() {
            every { standardPriorityQueue.offer(any(), any(), any()) } returns false
            every { repository.save(any()) } answers { firstArg() }
            queueService.poll(1) { _, _ -> throw InterruptedException("shut down") }
            verify { standardPriorityQueue.offer(queueItemStandardPrio, 5, TimeUnit.SECONDS) }
            verify { standardPrioJob.status = Status.FAILED }
            verify { repository.save(standardPrioJob) }
        }
    }

    @Nested
    inner class PollHighOrLowPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.poll() }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(2, expectHighPrio)).isTrue
            verify { standardPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns items from low priority queue if present and high priority queue is empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            assertThat(queueService.poll(2, expectLowPrio)).isTrue
            verify { standardPriorityQueue.poll() }
            verify { lowPriorityQueue.poll() }
        }

        @Test
        fun `returns null if all queues are empty`() {
            every { highPriorityQueue.poll() } returns null
            every { standardPriorityQueue.poll() } returns null
            every { lowPriorityQueue.poll() } returns null
            assertThat(queueService.poll(2, expectNone)).isFalse
            verify { standardPriorityQueue.poll() }
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
