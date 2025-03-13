// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.poll

import io.mockk.Called
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture

@ExtendWith(MockKExtension::class)
class JobPollerTest {

    @MockK
    private lateinit var queueService: QueueService

    @MockK
    private lateinit var encoreProperties: EncoreProperties

    @MockK
    private lateinit var encoreService: EncoreService

    @MockK
    private lateinit var scheduler: ThreadPoolTaskScheduler

    @InjectMockKs
    private lateinit var jobPoller: JobPoller

    private val encoreJob = EncoreJob(baseName = "TEST", outputFolder = "/test", profile = "test")

    private val queueItem = QueueItem(encoreJob.id.toString())

    private val capturedRunnables = mutableListOf<Runnable>()
    private val scheduledTasks = mutableListOf<ScheduledFuture<*>>()

    @BeforeEach
    fun setUp() {
        every { scheduler.scheduleWithFixedDelay(capture(capturedRunnables), any<Instant>(), any()) } answers {
            val scheduled = mockk<ScheduledFuture<*>>(relaxed = true)
            scheduledTasks.add(scheduled)
            scheduled
        }
        every { encoreService.encode(any(), any()) } just Runs
        every { queueService.poll(any(), captureLambda()) } answers {
            lambda<(QueueItem, EncoreJob) -> Unit>().captured.invoke(queueItem, encoreJob)
            true
        }
        every { queueService.handleOrphanedQueues() } just Runs
        every { queueService.migrateQueues() } just Runs
        every { encoreProperties.concurrency } returns 3
        every { encoreProperties.pollDelay } returns Duration.ofSeconds(1)
        every { encoreProperties.pollInitialDelay } returns Duration.ofSeconds(10)
        every { encoreProperties.pollQueue } returns null
        every { encoreProperties.pollDisabled } returns false
    }

    @Test
    fun doesNothingWhenPollDisabled() {
        every { encoreProperties.pollDisabled } returns true
        jobPoller.init()
        verify { scheduler wasNot Called }
        verify { encoreService wasNot Called }
        verify { queueService.handleOrphanedQueues() }
        assertThat(capturedRunnables).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun pollAll(thread: Int) {
        jobPoller.init()
        assertThat(capturedRunnables).hasSize(3)
        capturedRunnables[thread].run()

        verifySequence {
            queueService.migrateQueues()
            queueService.handleOrphanedQueues()
            queueService.poll(thread, any())
            encoreService.encode(queueItem, encoreJob)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun pollSpecific(queueNo: Int) {
        every { encoreProperties.pollQueue } returns queueNo
        jobPoller.init()
        assertThat(capturedRunnables).hasSize(1)
        capturedRunnables.first().run()
        verifySequence {
            queueService.migrateQueues()
            queueService.handleOrphanedQueues()
            queueService.poll(queueNo, any())
            encoreService.encode(queueItem, encoreJob)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `poll causes exception`(thread: Int) {
        every { queueService.poll(thread, any()) } throws Exception("error")
        jobPoller.init()

        capturedRunnables[thread].run()

        verifySequence {
            queueService.migrateQueues()
            queueService.handleOrphanedQueues()
            queueService.poll(thread, any())
        }
    }
}
