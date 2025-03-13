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
import io.mockk.mockkConstructor
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.BoundZSetOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations.TypedTuple
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.support.atomic.RedisAtomicInteger
import org.springframework.data.repository.findByIdOrNull
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.ApplicationShutdownException
import java.util.UUID

@ExtendWith(MockKExtension::class)
internal class QueueServiceTest {
    private val highPriorityQueue = mockk<BoundZSetOperations<String, QueueItem>>()
    private val standardPriorityQueue = mockk<BoundZSetOperations<String, QueueItem>>()
    private val lowPriorityQueue = mockk<BoundZSetOperations<String, QueueItem>>()
    val keyPrefix = "encore"
    private val encoreProperties = mockk<EncoreProperties> {
        every { concurrency } returns 3
        every { redisKeyPrefix } returns keyPrefix
        every { pollHigherPrio } returns true
    }

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, QueueItem>

    @MockK
    private lateinit var repository: EncoreJobRepository

    @MockK
    private lateinit var redisQueueMigrationScript: RedisScript<Boolean>

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
        every { highPriorityQueue.popMin() } returns TypedTuple.of(queueItemHighPrio, null)
        every { standardPriorityQueue.popMin() } returns TypedTuple.of(queueItemStandardPrio, null)
        every { lowPriorityQueue.popMin() } returns TypedTuple.of(queueItemLowPrio, null)
        every { repository.findByIdOrNull(UUID.fromString(queueItemHighPrio.id)) } returns highPrioJob
        every { repository.findByIdOrNull(UUID.fromString(queueItemStandardPrio.id)) } returns standardPrioJob
        every { repository.findByIdOrNull(UUID.fromString(queueItemLowPrio.id)) } returns lowPrioJob
        every { redisTemplate.boundZSetOps("$keyPrefix-queue-0") } returns highPriorityQueue
        every { redisTemplate.boundZSetOps("$keyPrefix-queue-1") } returns standardPriorityQueue
        every { redisTemplate.boundZSetOps("$keyPrefix-queue-2") } returns lowPriorityQueue
        every { redisTemplate.execute(redisQueueMigrationScript, any()) } returns false
    }

    @Nested
    inner class Init {

        @Test
        fun concurrencyReduced() {
            val concurrency = encoreProperties.concurrency
            mockkConstructor(RedisAtomicInteger::class)
            val connectionFactory = mockk<RedisConnectionFactory>(relaxed = true, relaxUnitFun = true)
            every { redisTemplate.connectionFactory } returns connectionFactory
            every { anyConstructed<RedisAtomicInteger>().getAndSet(concurrency) } returns concurrency + 2

            every { lowPriorityQueue.add(any()) } returns 1
            val orphanedQueue1 = mockk<BoundZSetOperations<String, QueueItem>>()
            val orphanedQueue2 = mockk<BoundZSetOperations<String, QueueItem>>()
            val orphanedQueue1Items = mockk<Set<TypedTuple<QueueItem>>> {
                every { size } returns 4
            }
            val orphanedQueue2Items = mockk<Set<TypedTuple<QueueItem>>> {
                every { size } returns 2
            }
            every { orphanedQueue1.popMin(Long.MAX_VALUE) } returns orphanedQueue1Items
            every { orphanedQueue2.popMin(Long.MAX_VALUE) } returns orphanedQueue2Items

            every { redisTemplate.boundZSetOps("$keyPrefix-queue-$concurrency") } returns orphanedQueue1
            every { redisTemplate.boundZSetOps("$keyPrefix-queue-${concurrency + 1}") } returns orphanedQueue2

            queueService.handleOrphanedQueues()

            verify { orphanedQueue1.popMin(Long.MAX_VALUE) }
            verify { orphanedQueue2.popMin(Long.MAX_VALUE) }
            verify { lowPriorityQueue.add(orphanedQueue1Items) }
            verify { lowPriorityQueue.add(orphanedQueue2Items) }
        }
    }

    @Nested
    inner class PollHighPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.popMin() }
            verify { standardPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(0, expectHighPrio)).isTrue
        }

        @Test
        fun `returns null if high priority queue is empty`() {
            every { highPriorityQueue.popMin() } returns null
            assertThat(queueService.poll(0, expectNone)).isFalse
        }
    }

    @Nested
    inner class PollHighOrStandardPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.popMin() }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(1, expectHighPrio)).isTrue
            verify { standardPriorityQueue wasNot Called }
        }

        @Test
        fun `returns items from standard priority queue if high priority queue is empty`() {
            every { highPriorityQueue.popMin() } returns null
            assertThat(queueService.poll(1, expectStandardPrio)).isTrue
            verify { standardPriorityQueue.popMin() }
        }

        @Test
        fun `returns null if both queues empty`() {
            every { highPriorityQueue.popMin() } returns null
            every { standardPriorityQueue.popMin() } returns null
            assertThat(queueService.poll(1, expectNone)).isFalse
            verify { standardPriorityQueue.popMin() }
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
            verify { standardPriorityQueue.popMin() }
            verify { highPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns item from queue`() {
            assertThat(queueService.poll(1, expectStandardPrio)).isTrue
        }

        @Test
        fun `empty queue`() {
            every { standardPriorityQueue.popMin() } returns null
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
        fun `reenqueue on shutdown`() {
            every { standardPriorityQueue.add(any(), any()) } returns true
            every { repository.save(any()) } answers { firstArg() }
            assertThat(queueService.poll(1) { _, _ -> throw ApplicationShutdownException() }).isFalse()
            verify { standardPriorityQueue.add(queueItemStandardPrio, (100 - queueItemStandardPrio.priority).toDouble()) }
            verify { standardPrioJob.status = Status.QUEUED }
            verify { repository.save(standardPrioJob) }
        }

        @Test
        fun `reenqueue on shutdown fails`() {
            every { standardPriorityQueue.add(any(), any()) } returns false
            every { repository.save(any()) } answers { firstArg() }
            assertThat(queueService.poll(1) { _, _ -> throw ApplicationShutdownException() }).isFalse()
            verify { standardPriorityQueue.add(queueItemStandardPrio, (100 - queueItemStandardPrio.priority).toDouble()) }
            verify { standardPrioJob.status = Status.FAILED }
            verify { repository.save(standardPrioJob) }
        }
    }

    @Nested
    inner class PollHighOrLowPriorityQueue {

        @AfterEach
        fun tearDown() {
            verify { highPriorityQueue.popMin() }
        }

        @Test
        fun `returns item from high priority queue if any present`() {
            assertThat(queueService.poll(2, expectHighPrio)).isTrue
            verify { standardPriorityQueue wasNot Called }
            verify { lowPriorityQueue wasNot Called }
        }

        @Test
        fun `returns items from low priority queue if present and high priority queue is empty`() {
            every { highPriorityQueue.popMin() } returns null
            every { standardPriorityQueue.popMin() } returns null
            assertThat(queueService.poll(2, expectLowPrio)).isTrue
            verify { standardPriorityQueue.popMin() }
            verify { lowPriorityQueue.popMin() }
        }

        @Test
        fun `returns null if all queues are empty`() {
            every { highPriorityQueue.popMin() } returns null
            every { standardPriorityQueue.popMin() } returns null
            every { lowPriorityQueue.popMin() } returns null
            assertThat(queueService.poll(2, expectNone)).isFalse
            verify { standardPriorityQueue.popMin() }
            verify { lowPriorityQueue.popMin() }
        }
    }

    @Nested
    inner class Enqueue {

        @Test
        fun `low priority job is enqueued on low priority queue`() {
            val job = defaultEncoreJob(10)
            every { lowPriorityQueue.add(any(), any()) } returns true
            queueService.enqueue(job)
            val expectedQueueItem = expectedQueueItem(job)
            verify { lowPriorityQueue.add(expectedQueueItem, (100 - expectedQueueItem.priority).toDouble()) }
            verify(exactly = 0) { standardPriorityQueue.add(any(), any()) }
            verify(exactly = 0) { highPriorityQueue.add(any(), any()) }
        }

        @Test
        fun `standard priority job is enqueued on standard priority queue`() {
            val job = defaultEncoreJob(55)
            every { standardPriorityQueue.add(any(), any()) } returns true
            queueService.enqueue(job)
            verify { standardPriorityQueue.add(expectedQueueItem(job), 45.0) }
            verify(exactly = 0) { highPriorityQueue.add(any(), any()) }
            verify(exactly = 0) { lowPriorityQueue.add(any(), any()) }
        }

        @Test
        fun `high priority job is enqueued on high priority queue`() {
            val job = defaultEncoreJob(priority = 90)
            every { highPriorityQueue.add(any(), any()) } returns true
            queueService.enqueue(job)
            verify { highPriorityQueue.add(expectedQueueItem(job), 10.0) }
            verify(exactly = 0) { standardPriorityQueue.add(any(), any()) }
            verify(exactly = 0) { lowPriorityQueue.add(any(), any()) }
        }

        private fun expectedQueueItem(job: EncoreJob) =
            QueueItem(
                id = job.id.toString(),
                priority = job.priority,
                created = job.createdDate.toLocalDateTime(),
            )
    }
}
