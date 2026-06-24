// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.service.queue.QueueService
import java.time.LocalDateTime

@WebMvcTest(controllers = [QueueController::class])
class QueueControllerTest {

    @MockkBean
    lateinit var queueService: QueueService

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `getQueue returns list of queue items`() {
        val item = QueueItem(id = "abc-123", priority = 50, created = LocalDateTime.of(2026, 1, 1, 12, 0))
        every { queueService.getQueue() } returns listOf(item)

        mockMvc.get("/queue") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].id") { value("abc-123") }
            jsonPath("$[0].priority") { value(50) }
        }
    }

    @Test
    fun `getQueue returns empty list when no items`() {
        every { queueService.getQueue() } returns emptyList()

        mockMvc.get("/queue") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isEmpty() }
        }
    }

    @Test
    fun `getQueueCount returns count per queue and total`() {
        every { queueService.getQueueCount() } returns mapOf(
            "prefix:queue:0" to 3L,
            "prefix:queue:1" to 1L,
            "total" to 4L,
        )

        mockMvc.get("/queueCount") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.total") { value(4) }
            jsonPath("$.['prefix:queue:0']") { value(3) }
            jsonPath("$.['prefix:queue:1']") { value(1) }
        }
    }
}
