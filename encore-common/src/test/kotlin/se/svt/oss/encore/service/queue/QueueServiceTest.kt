// SPDX-FileCopyrightText: 2023 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.queue

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.lettuce.core.FlushMode
import io.lettuce.core.api.StatefulRedisConnection
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import se.svt.oss.encore.RedisExtension
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.redis.RedisService
import se.svt.oss.encore.service.ApplicationShutdownException
import java.util.UUID

@SpringBootTest
@ExtendWith(RedisExtension::class)
@ActiveProfiles("test")
class QueueServiceTest {

    @MockkBean(relaxed = true)
    lateinit var redisService: RedisService

    @MockkSpyBean
    lateinit var encoreProperties: EncoreProperties

    @Autowired
    lateinit var redisConnection: StatefulRedisConnection<String, String>

    @Autowired
    lateinit var queueService: QueueService

    @AfterEach
    fun tearDown() {
        redisConnection.sync().flushdb(FlushMode.SYNC)
    }

    @BeforeEach
    fun setUp() {
        every { encoreProperties.concurrency } returns 3
        every { redisService.createIndex() } just Runs
    }

    private fun job(priority: Int = 0, status: Status = Status.QUEUED) = EncoreJob(
        profile = "test",
        outputFolder = "/output",
        baseName = "TEST",
        priority = priority,
        inputs = listOf(AudioVideoInput(uri = "/input/test.mp4")),
    ).apply { this.status = status }

    @Test
    fun `enqueue adds job to correct queue and getQueue returns it`() {
        val job = job(priority = 0)
        queueService.enqueue(job)

        val queue = queueService.getQueue()

        assertThat(queue).hasSize(1)
        assertThat(queue.first().id).isEqualTo(job.id.toString())
    }

    @Test
    fun `enqueue high-priority job goes to lower queue number`() {
        val lowPrioJob = job(priority = 0)
        val highPrioJob = job(priority = 100)
        queueService.enqueue(lowPrioJob)
        queueService.enqueue(highPrioJob)

        val queue = queueService.getQueue()

        assertThat(queue).hasSize(2)
        // high priority (100) -> queue 0, low priority (0) -> queue 2
        assertThat(queue.first().id).isEqualTo(highPrioJob.id.toString())
    }

    @Test
    fun `getQueueCount returns count per queue and total`() {
        queueService.enqueue(job(priority = 0))
        queueService.enqueue(job(priority = 100))

        val counts = queueService.getQueueCount()

        assertThat(counts["total"]).isEqualTo(2)
    }

    @Test
    fun `poll returns false when queue is empty`() {
        val result = queueService.poll(2) { _, _ -> }

        assertThat(result).isFalse()
    }

    @Test
    fun `poll invokes action and returns true when job is found`() {
        val job = job()
        queueService.enqueue(job)
        every { redisService.findByIdOrNull(job.id) } returns job

        var invokedWith: EncoreJob? = null
        val result = queueService.poll(2) { _, encoreJob -> invokedWith = encoreJob }

        assertThat(result).isTrue()
        assertThat(invokedWith?.id).isEqualTo(job.id)
    }

    @Test
    fun `poll retries when job not found at first`() {
        val job = job()
        queueService.enqueue(job)
        every { redisService.findByIdOrNull(job.id) } returns null andThen job

        var invokedWith: EncoreJob? = null
        val result = queueService.poll(2) { _, encoreJob -> invokedWith = encoreJob }

        assertThat(result).isTrue()
        assertThat(invokedWith?.id).isEqualTo(job.id)
    }

    @Test
    fun `poll retries when job not found and fails at last`() {
        val job = job()
        queueService.enqueue(job)
        every { redisService.findByIdOrNull(job.id) } returns null

        assertThatThrownBy { queueService.poll(2) { _, _ -> } }
            .hasMessage("Job ${job.id} does not exist")
        verify(exactly = 2) { redisService.findByIdOrNull(job.id) }
    }

    @Test
    fun `poll skips cancelled job and returns true`() {
        val job = job(status = Status.CANCELLED)
        queueService.enqueue(job)
        every { redisService.findByIdOrNull(job.id) } returns job

        val result = queueService.poll(2) { _, _ -> error("should not be called") }

        assertThat(result).isTrue()
    }

    @Test
    fun `poll with pollHigherPrio polls lower queue numbers first`() {
        val highPrioJob = job(priority = 100) // goes to queue 0
        queueService.enqueue(highPrioJob)
        every { encoreProperties.pollHigherPrio } returns true
        every { redisService.findByIdOrNull(highPrioJob.id) } returns highPrioJob

        var invokedWith: EncoreJob? = null
        val result = queueService.poll(2) { _, encoreJob -> invokedWith = encoreJob }

        assertThat(result).isTrue()
        assertThat(invokedWith?.id).isEqualTo(highPrioJob.id)
    }

    @Test
    fun `poll with pollHigherPrio false polls only requested queue`() {
        val highPrioJob = job(priority = 100) // goes to queue 0
        queueService.enqueue(highPrioJob)
        val lowPrioJob = job(priority = 0) // goes to queue 2
        queueService.enqueue(lowPrioJob)
        every { encoreProperties.pollHigherPrio } returns false
        every { redisService.findByIdOrNull(lowPrioJob.id) } returns lowPrioJob

        var invokedWith: EncoreJob? = null
        val result = queueService.poll(2) { _, encoreJob -> invokedWith = encoreJob }

        assertThat(result).isTrue()
        assertThat(invokedWith?.id).isEqualTo(lowPrioJob.id)
    }

    @Test
    fun `poll re-enqueues job and returns false on ApplicationShutdownException`() {
        val job = job()
        queueService.enqueue(job)
        every { redisService.findByIdOrNull(job.id) } returns job
        every { redisService.save(any()) } just runs

        val result = queueService.poll(2) { _, _ -> throw ApplicationShutdownException() }

        assertThat(result).isFalse()
        assertThat(queueService.getQueue()).hasSize(1)
        verify { redisService.save(any()) }
    }

    @Test
    fun `poll skips failed segment job`() {
        val job = job(status = Status.FAILED)
        val segmentItem = QueueItem(id = job.id.toString(), priority = 0, segment = 1)
        queueService.enqueue(segmentItem)
        every { redisService.findByIdOrNull(job.id) } returns job

        val result = queueService.poll(2) { _, _ -> error("should not be called") }

        assertThat(result).isTrue()
    }

    @Test
    fun `handle orphaned queues move items to higher prio queues`() {
        queueService.handleOrphanedQueues() // init concurrency in empty redis
        val queueItemLowPrio = QueueItem(id = UUID.randomUUID().toString(), priority = 0)
        val queueItemMediumPrio = QueueItem(id = UUID.randomUUID().toString(), priority = 50)
        val queueItemHighPrio = QueueItem(id = UUID.randomUUID().toString(), priority = 100)
        queueService.enqueue(queueItemLowPrio)
        queueService.enqueue(queueItemMediumPrio)
        queueService.enqueue(queueItemHighPrio)
        assertThat(queueService.getQueueCount()).isEqualTo(
            mapOf(
                "encore:queue:0" to 1L,
                "encore:queue:1" to 1L,
                "encore:queue:2" to 1L,
                "total" to 3L,
            ),
        )

        every { encoreProperties.concurrency } returns 2
        queueService.handleOrphanedQueues()

        assertThat(queueService.getQueueCount()).isEqualTo(
            mapOf(
                "encore:queue:0" to 1L,
                "encore:queue:1" to 2L,
                "total" to 3L,
            ),
        )

        assertThat(queueService.getQueue())
            .containsExactlyInAnyOrder(queueItemLowPrio, queueItemMediumPrio, queueItemHighPrio)
    }
}
