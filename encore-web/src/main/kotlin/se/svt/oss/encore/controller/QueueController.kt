// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import io.swagger.v3.oas.annotations.Operation
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import se.svt.oss.encore.service.queue.QueueService

@CrossOrigin
@RestController
class QueueController(
    private val queueService: QueueService,
) {
    @Operation(summary = "Get Queue", description = "Returns a list of QueueItems", tags = ["queue"])
    @GetMapping("/queue")
    fun getQueue() = queueService.getQueue()

    @Operation(summary = "Get Queue count", tags = ["queue"])
    @GetMapping("/queueCount")
    fun getQueueCount() = queueService.getQueueCount()
}
