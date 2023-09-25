// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.handlers

import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.queue.QueueService

@ExtendWith(MockKExtension::class)
class EncoreJobHandlerTest {

    @MockK
    private lateinit var queueService: QueueService

    @MockK
    private lateinit var repository: EncoreJobRepository

    @InjectMockKs
    private lateinit var encoreJobHandler: EncoreJobHandler

    private val job = EncoreJob(baseName = "TEST", outputFolder = "/test", profile = "test")

    @BeforeEach
    fun setUp() {
        every { repository.save(job) } returns job
    }

    @Test
    fun `successfully creates`() {
        every { queueService.enqueue(job) } just Runs

        encoreJobHandler.onAfterCreate(job)
        assertThat(job.status).isEqualTo(Status.QUEUED)

        verify { repository.save(job) }
        verify { queueService.enqueue(job) }
    }

    @Test
    fun `enqueue fails`() {
        every { queueService.enqueue(job) } throws Exception("error")

        encoreJobHandler.onAfterCreate(job)

        assertThat(job.status).isEqualTo(Status.FAILED)

        verify { repository.save(job) }
        verify { queueService.enqueue(job) }
    }
}
