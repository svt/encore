// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import io.swagger.v3.oas.annotations.Operation
import mu.withLoggingContext
import org.redisson.api.RedissonClient
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.queue.QueueService
import java.util.UUID

@CrossOrigin
@RestController
class EncoreController(
    private val repository: EncoreJobRepository,
    private val redissonClient: RedissonClient,
    private val queueService: QueueService,
) {

    @Operation(summary = "Get Queues", description = "Returns a list of queues (QueueItems)", tags = ["queue"])
    @GetMapping("/queue")
    fun getQueue() = queueService.getQueue()

    @Operation(summary = "Cancel an EncoreJob", description = "Cancels an EncoreJob with thw given JobId", tags = ["encorejob"])
    @PostMapping("/encoreJobs/{jobId}/cancel")
    fun cancel(@PathVariable("jobId") jobId: UUID): ResponseEntity<String> =
        try {
            repository.findByIdOrNull(jobId)?.let {
                withLoggingContext(it.contextMap) {
                    when (it.status) {
                        Status.NEW, Status.QUEUED -> {
                            it.status = Status.CANCELLED
                            repository.save(it)
                            sendCancelEvent(jobId)
                            ResponseEntity.ok("Ok")
                        }
                        Status.IN_PROGRESS -> {
                            sendCancelEvent(jobId)
                            ResponseEntity.ok("Ok")
                        }
                        else -> {
                            ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body("Cannot cancel job with status ${it.status}!")
                        }
                    }
                }
            } ?: ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Job $jobId does not exist!")
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(e.message)
        }

    private fun sendCancelEvent(jobId: UUID) {
        redissonClient.getTopic("cancel").publish(CancelEvent(jobId))
    }
}
