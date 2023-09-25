// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.SpringApplication
import org.springframework.context.ApplicationContext
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService

@ExtendWith(MockKExtension::class)
class EncoreWorkerApplicationTest {

    @MockK
    private lateinit var queueService: QueueService

    @MockK
    private lateinit var encoreService: EncoreService

    @MockK
    private lateinit var applicationContext: ApplicationContext

    @MockK
    private lateinit var encoreProperties: EncoreProperties

    @InjectMockKs
    lateinit var application: EncoreWorkerApplication

    @BeforeEach
    fun setUp() {
        every { queueService.poll(any(), any()) } returns true andThen false
        every { encoreProperties.pollQueue } returns 1
        every { encoreProperties.workerDrainQueue } returns false
        mockkStatic(SpringApplication::class)
        every { SpringApplication.exit(any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun pollOnce() {
        application.run()
        verify(exactly = 1) { queueService.poll(1, encoreService::encode) }
        verify { SpringApplication.exit(applicationContext) }
    }

    @Test
    fun defaultsToQueue0() {
        every { encoreProperties.pollQueue } returns null
        application.run()
        verify(exactly = 1) { queueService.poll(0, encoreService::encode) }
        verify { SpringApplication.exit(applicationContext) }
    }

    @Test
    fun drainQueue() {
        every { encoreProperties.workerDrainQueue } returns true
        application.run()
        verify(exactly = 2) { queueService.poll(1, encoreService::encode) }
        verify { SpringApplication.exit(applicationContext) }
    }
}
