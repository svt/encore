// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.handlers

import mu.KotlinLogging
import mu.withLoggingContext
import org.springframework.data.rest.core.annotation.HandleAfterCreate
import org.springframework.data.rest.core.annotation.RepositoryEventHandler
import org.springframework.stereotype.Component
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.repository.EncoreJobRepository
import se.svt.oss.encore.service.queue.QueueService

@Component
@RepositoryEventHandler
class EncoreJobHandler(
    private val queueService: QueueService,
    private val repository: EncoreJobRepository
) {
    private val log = KotlinLogging.logger { }

    @HandleAfterCreate
    fun onAfterCreate(encoreJob: EncoreJob) {
        withLoggingContext(encoreJob.contextMap) {
            try {
                log.info { "Adding job to queue.. $encoreJob" }
                queueService.enqueue(encoreJob)
                log.info { "Added job to queue" }
                encoreJob.status = Status.QUEUED
                repository.save(encoreJob)
            } catch (e: Exception) {
                val message = "Failed to queue: ${e.message}"
                log.error(e) { message }
                encoreJob.status = Status.FAILED
                encoreJob.message = message
                repository.save(encoreJob)
            }
        }
    }
}
