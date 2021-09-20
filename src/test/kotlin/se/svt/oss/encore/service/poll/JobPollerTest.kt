// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.poll

import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService
import java.time.Instant
import java.util.concurrent.ScheduledFuture

@FlowPreview
@ExperimentalCoroutinesApi
@ExtendWith(MockKExtension::class)
class JobPollerTest {

    @MockK
    private lateinit var repository: EncoreJobRepository

    @MockK
    private lateinit var queueService: QueueService

    private val encoreProperties = EncoreProperties(concurrency = 3)

    @MockK
    private lateinit var encoreService: EncoreService

    @MockK
    private lateinit var scheduler: ThreadPoolTaskScheduler

    @InjectMockKs
    private lateinit var jobPoller: JobPoller

    private val encoreJob = defaultEncoreJob()

    private val queueItem = QueueItem(encoreJob.id.toString())

    private val capturedRunnables = mutableListOf<Runnable>()
    private val scheduledTasks = mutableListOf<ScheduledFuture<*>>()

    @BeforeEach
    fun setUp() {
        every { scheduler.scheduleWithFixedDelay(capture(capturedRunnables), any<Instant>(), any()) } answers {
            val scheduled = mockk<ScheduledFuture<*>>()
            scheduledTasks.add(scheduled)
            scheduled
        }
        every { repository.findByIdOrNull(encoreJob.id) } returns encoreJob
        every { encoreService.encode(encoreJob) } just Runs
        every { queueService.poll(any()) } returns queueItem
        jobPoller.init()
        assertThat(capturedRunnables).hasSize(3)
    }

    @Test
    fun testDestroy() {
        assertThat(scheduledTasks).hasSize(3)
        scheduledTasks.forEach {
            every { it.cancel(false) } returns true
        }
        jobPoller.destroy()
        scheduledTasks.forEach {
            verify { it.cancel(false) }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun poll(thread: Int) {
        capturedRunnables[thread].run()

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
            encoreService.encode(encoreJob)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `poll causes exception`(thread: Int) {
        every { queueService.poll(thread) } throws Exception("error")

        capturedRunnables[thread].run()

        verifySequence {
            queueService.poll(thread)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun repositoryReturnsNull(thread: Int) {
        every { repository.findByIdOrNull(encoreJob.id) } returns null

        capturedRunnables[thread].run()

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
            repository.findByIdOrNull(encoreJob.id)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun repositoryRetryWorks(thread: Int) {
        every { repository.findByIdOrNull(encoreJob.id) } returns null andThen encoreJob

        capturedRunnables[thread].run()

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
            repository.findByIdOrNull(encoreJob.id)
            encoreService.encode(encoreJob)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `cancelled job`(thread: Int) {
        encoreJob.status = Status.CANCELLED

        capturedRunnables[thread].run()
        assertThat(encoreJob.status).isEqualTo(Status.CANCELLED)

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `interrupted job re-enqueues`(thread: Int) {
        every { encoreService.encode(encoreJob) } throws InterruptedException()
        every { queueService.enqueue(encoreJob) } just Runs

        capturedRunnables[thread].run()
        assertThat(encoreJob.status).isEqualTo(Status.NEW)

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
            queueService.enqueue(encoreJob)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `interrupted job re-enqueue fails`(thread: Int) {
        every { encoreService.encode(encoreJob) } throws InterruptedException()
        every { queueService.enqueue(encoreJob) } throws Exception("error")
        every { repository.save(encoreJob) } returns encoreJob

        capturedRunnables[thread].run()
        assertThat(encoreJob.status).isEqualTo(Status.FAILED)

        verifySequence {
            queueService.poll(thread)
            repository.findByIdOrNull(encoreJob.id)
            queueService.enqueue(encoreJob)
            repository.save(encoreJob)
        }
    }
}
