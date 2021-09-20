// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.queue.QueueService
import java.util.UUID

@WebMvcTest(EncoreController::class)
@AutoConfigureMockMvc(addFilters = false)
class EncoreControllerTest {

    @MockkBean
    private lateinit var encoreJobRepository: EncoreJobRepository

    @MockkBean
    private lateinit var redissonClient: RedissonClient

    @MockkBean
    private lateinit var queueService: QueueService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Nested
    inner class Queue {

        @Test
        fun getQueue() {
            val queue = listOf(QueueItem("1"), QueueItem("2"))
            every { queueService.getQueue() } returns queue

            mockMvc.get("/queue") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { is2xxSuccessful() }
                content {
                    contentType(MediaType.APPLICATION_JSON)
                    json(objectMapper.writeValueAsString(queue))
                }
            }

            verifySequence { queueService.getQueue() }
        }
    }

    @Nested
    inner class Cancel {
        private val encoreJob = defaultEncoreJob()

        private fun cancelAndAssertStatus(jobId: UUID, expectedStatus: Int) {
            mockMvc.post("/encoreJobs/$jobId/cancel") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isEqualTo(expectedStatus) }
            }
        }

        @ParameterizedTest
        @ValueSource(strings = ["NEW", "QUEUED"])
        fun cancelNewEvent(statusString: String) {
            val encoreJob = encoreJob.apply { status = Status.valueOf(statusString) }
            val cancelEvent = CancelEvent(encoreJob.id)
            every { encoreJobRepository.findByIdOrNull(encoreJob.id) } returns encoreJob
            every { encoreJobRepository.save(encoreJob) } returns encoreJob
            every { redissonClient.getTopic("cancel").publish(cancelEvent) } returns 5

            cancelAndAssertStatus(encoreJob.id, 200)

            verify { encoreJobRepository.findByIdOrNull(encoreJob.id) }
            verify { encoreJobRepository.save(encoreJob) }
            verify { redissonClient.getTopic("cancel").publish(cancelEvent) }
        }

        @Test
        fun cancelInProgress() {
            val encoreJob = encoreJob.apply { status = Status.IN_PROGRESS }
            val cancelEvent = CancelEvent(encoreJob.id)
            every { encoreJobRepository.findByIdOrNull(encoreJob.id) } returns encoreJob
            every { redissonClient.getTopic("cancel").publish(cancelEvent) } returns 5

            cancelAndAssertStatus(encoreJob.id, 200)

            verify { encoreJobRepository.findByIdOrNull(encoreJob.id) }
            verify(exactly = 0) { encoreJobRepository.save(encoreJob) }
            verify { redissonClient.getTopic("cancel").publish(cancelEvent) }
        }

        @Test
        fun cancelCausesConflict() {
            val encoreJob = encoreJob.apply { status = Status.FAILED }
            every { encoreJobRepository.findByIdOrNull(encoreJob.id) } returns encoreJob

            cancelAndAssertStatus(encoreJob.id, 409)

            verify { encoreJobRepository.findByIdOrNull(encoreJob.id) }
            verify(exactly = 0) { encoreJobRepository.save(encoreJob) }
        }

        @Test
        fun cancelJobNotFound() {
            val encoreJob = encoreJob.apply { status = Status.FAILED }
            every { encoreJobRepository.findByIdOrNull(encoreJob.id) } returns null

            cancelAndAssertStatus(encoreJob.id, 404)

            verify { encoreJobRepository.findByIdOrNull(encoreJob.id) }
        }

        @Test
        fun cancelCausesException() {
            val encoreJob = encoreJob.apply { status = Status.FAILED }
            every { encoreJobRepository.findByIdOrNull(encoreJob.id) } throws Exception("error")

            cancelAndAssertStatus(encoreJob.id, 500)

            verify { encoreJobRepository.findByIdOrNull(encoreJob.id) }
        }
    }
}
