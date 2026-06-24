// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.redis.InvalidQueryException
import se.svt.oss.encore.redis.RedisService
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.queue.QueueService
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@WebMvcTest(controllers = [EncoreController::class])
class EncoreControllerTest {

    @MockkBean
    lateinit var redisService: RedisService

    @MockkBean
    lateinit var queueService: QueueService

    @MockkBean
    lateinit var callbackService: CallbackService

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    private fun encoreJob(
        id: UUID = UUID.randomUUID(),
        status: Status = Status.NEW,
    ): EncoreJob =
        EncoreJob(
            id = id,
            externalId = UUID.randomUUID().toString(),
            profile = "program-x265-hqsb",
            outputFolder = "/test-folder",
            baseName = "TEST",
            inputs = listOf(AudioVideoInput(uri = "/input.mp4")),
        ).apply { this.status = status }

    @Test
    fun `createJob returns 201 and enqueues job`() {
        val jsonRequest = """
            {
              "profile": "program-x265-hqsb",
              "outputFolder": "/test-folder",
              "baseName": "TEST",
              "inputs": [ { "uri": "/input.mp4", "type": "AudioVideo" } ]
            }
        """.trimIndent()

        every { redisService.save(any()) } just runs
        every { queueService.enqueue(any<EncoreJob>()) } just runs

        mockMvc.post("/encoreJobs") {
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.APPLICATION_JSON
            content = jsonRequest
        }.andExpect {
            status { isCreated() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.status") { value(Status.QUEUED.name) }
            jsonPath("$._links.self.href") { value(matchesPattern("http://localhost/encoreJobs/[0-9a-fA-F\\-]{36}")) }
        }

        verify { redisService.save(any()) }
        verify { queueService.enqueue(any<EncoreJob>()) }
    }

    @Test
    fun `getJob returns 200 with self link`() {
        val job = encoreJob(id = UUID.randomUUID(), status = Status.QUEUED)
        every { redisService.findByIdOrNull(job.id) } returns job

        mockMvc.get("/encoreJobs/${job.id}") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(job.id.toString()) }
            jsonPath("$.status") { value(Status.QUEUED.name) }
            jsonPath("$._links.self.href") { value("http://localhost/encoreJobs/${job.id}") }
        }
    }

    @Test
    fun `getJob returns 404 ProblemDetail when job not found`() {
        val id = UUID.randomUUID()
        every { redisService.findByIdOrNull(id) } returns null

        mockMvc.get("/encoreJobs/$id") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.status") { value(404) }
            jsonPath("$.detail") { value("Job with id $id not found") }
        }
    }

    @Test
    fun `getJobs returns paged result`() {
        val job = encoreJob(status = Status.QUEUED)
        val pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate")))
        val page = PageImpl(listOf(job), pageRequest, 1)

        every { redisService.findAll(any()) } returns page

        mockMvc.get("/encoreJobs") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._embedded.encoreJobs[0].id") { value(job.id.toString()) }
            jsonPath("$._embedded.encoreJobs[0].status") { value(Status.QUEUED.name) }
            jsonPath("$._links.search.href") { value("http://localhost/encoreJobs/search") }
        }

        verify { redisService.findAll(any()) }
    }

    @Test
    fun `findByStatus delegates to redisService`() {
        val job = encoreJob(status = Status.SUCCESSFUL)
        val pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate")))
        val page = PageImpl(listOf(job), pageRequest, 1)

        every { redisService.findByStatus(Status.SUCCESSFUL, pageRequest) } returns page

        mockMvc.get("/encoreJobs/search/findByStatus") {
            param("status", "SUCCESSFUL")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._embedded.encoreJobs[0].id") { value(job.id.toString()) }
            jsonPath("$._embedded.encoreJobs[0].status") { value("SUCCESSFUL") }
        }

        verify { redisService.findByStatus(Status.SUCCESSFUL, pageRequest) }
    }

    @Test
    fun `getJobs returns ProblemDetail 400 for invalid sort field`() {
        every { redisService.findAll(any()) } throws InvalidQueryException("Bad query!")

        mockMvc.get("/encoreJobs") {
            param("sort", "foo,asc")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            content {
                contentTypeCompatibleWith("application/problem+json")
            }
            jsonPath("$.detail") { value("Bad query!") }
        }
    }

    @Test
    fun `cancel returns 200 when job is NEW`() {
        val jobId = UUID.randomUUID()
        val job = mockk<EncoreJob> {
            every { status } returns Status.NEW
            every { contextMap } returns emptyMap()
            every { updateStatus(any(), any()) } just Runs
        }

        every { redisService.findByIdOrNull(jobId) } returns job
        every { redisService.save(job) } just runs
        every { redisService.cancel(jobId) } returns 0
        every { callbackService.sendProgressCallback(any()) } just runs

        mockMvc.post("/encoreJobs/$jobId/cancel") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify { job.updateStatus(Status.CANCELLED) }
        verify { redisService.save(job) }
        verify { redisService.cancel(jobId) }
        verify { callbackService.sendProgressCallback(job) }
    }

    @Test
    fun `cancel returns 200 when job is IN_PROGRESS`() {
        val jobId = UUID.randomUUID()

        every { redisService.findByIdOrNull(jobId) } returns mockk {
            every { status } returns Status.IN_PROGRESS
            every { contextMap } returns emptyMap()
        }
        every { redisService.cancel(jobId) } returns 1

        mockMvc.post("/encoreJobs/$jobId/cancel") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        verify(exactly = 0) { redisService.save(any()) }
        verify { redisService.cancel(jobId) }
    }

    @Test
    fun `cancel returns 404 ProblemDetail when job does not exist`() {
        val jobId = UUID.randomUUID()
        every { redisService.findByIdOrNull(jobId) } returns null

        mockMvc.post("/encoreJobs/$jobId/cancel") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.status") { value(404) }
            jsonPath("$.detail") { value("Job $jobId does not exist!") }
        }
    }

    @Test
    fun `cancel returns 409 ProblemDetail when status is not cancellable`() {
        val jobId = UUID.randomUUID()

        every { redisService.findByIdOrNull(jobId) } returns mockk {
            every { status } returns Status.SUCCESSFUL
            every { contextMap } returns emptyMap()
        }

        mockMvc.post("/encoreJobs/$jobId/cancel") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.status") { value(409) }
            jsonPath("$.detail") {
                value("Cannot cancel job with status ${Status.SUCCESSFUL}!")
            }
        }
    }

    @Test
    fun `findByExternalId delegates to redisService`() {
        val job = encoreJob()
        val pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate")))
        val page = PageImpl(listOf(job), pageRequest, 1)

        every { redisService.findByExternalId(job.externalId!!, pageRequest) } returns page

        mockMvc.get("/encoreJobs/search/findByExternalId") {
            param("externalId", job.externalId!!)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._embedded.encoreJobs[0].id") { value(job.id.toString()) }
            jsonPath("$._embedded.encoreJobs[0].externalId") { value(job.externalId) }
        }

        verify { redisService.findByExternalId(job.externalId!!, pageRequest) }
    }

    @Test
    fun `findByProfile delegates to redisService`() {
        val job = encoreJob()
        val pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate")))
        val page = PageImpl(listOf(job), pageRequest, 1)

        every { redisService.findByProfile("program-x265-hqsb", pageRequest) } returns page

        mockMvc.get("/encoreJobs/search/findByProfile") {
            param("profile", "program-x265-hqsb")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._embedded.encoreJobs[0].id") { value(job.id.toString()) }
            jsonPath("$._embedded.encoreJobs[0].profile") { value("program-x265-hqsb") }
        }

        verify { redisService.findByProfile("program-x265-hqsb", pageRequest) }
    }

    @Test
    fun `findByBaseName delegates to redisService`() {
        val job = encoreJob()
        val pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate")))
        val page = PageImpl(listOf(job), pageRequest, 1)

        every { redisService.findByBaseName("TEST", pageRequest) } returns page

        mockMvc.get("/encoreJobs/search/findByBaseName") {
            param("baseName", "TEST")
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._embedded.encoreJobs[0].id") { value(job.id.toString()) }
            jsonPath("$._embedded.encoreJobs[0].baseName") { value("TEST") }
        }

        verify { redisService.findByBaseName("TEST", pageRequest) }
    }

    @Test
    fun `listSearches returns links for all search endpoints`() {
        mockMvc.get("/encoreJobs/search") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$._links.findByStatus.href") { value("http://localhost/encoreJobs/search/findByStatus{?status}") }
            jsonPath("$._links.findByProfile.href") { value("http://localhost/encoreJobs/search/findByProfile{?profile}") }
            jsonPath("$._links.findByBaseName.href") { value("http://localhost/encoreJobs/search/findByBaseName{?baseName}") }
            jsonPath("$._links.findByExternalId.href") { value("http://localhost/encoreJobs/search/findByExternalId{?externalId}") }
        }
    }

    @Test
    fun `getJobs returns ProblemDetail 500 for runtime exception`() {
        every { redisService.findAll(any()) } throws RuntimeException("OH NO!")

        mockMvc.get("/encoreJobs") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { is5xxServerError() }
            content { contentTypeCompatibleWith("application/problem+json") }
            jsonPath("$.status") { value(500) }
            jsonPath("$.detail") {
                value(
                    matchesPattern(
                        "An unexpected error occurred \\(errorId=[0-9a-fA-F\\-]{36}\\)",
                    ),
                )
            }
        }
    }
}
