// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.cancellation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import se.svt.oss.encore.model.CancelEvent
import java.util.UUID

private val log = KotlinLogging.logger {}

class CancellationListener(
    private val objectMapper: ObjectMapper,
    private val encoreJobId: UUID,
    private val coroutineJob: Job,
) : MessageListener {

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val jobId = objectMapper.readValue<CancelEvent>(message.body).jobId
        if (jobId == encoreJobId) {
            log.info { "Received cancel event for job $jobId" }
            coroutineJob.cancel()
        }
    }
}
