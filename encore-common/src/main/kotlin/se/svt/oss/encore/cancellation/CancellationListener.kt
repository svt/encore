// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.cancellation

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.pubsub.RedisPubSubAdapter
import kotlinx.coroutines.Job
import se.svt.oss.encore.model.CancelEvent
import java.util.UUID

private val log = KotlinLogging.logger {}

data class CancellationListener(
    val encoreJobId: UUID,
    private val coroutineJob: Job,
) : RedisPubSubAdapter<String, CancelEvent>() {

    override fun smessage(shardChannel: String, message: CancelEvent) {
        if (message.jobId == encoreJobId) {
            log.info { "Cancelling job ${message.jobId}" }
            coroutineJob.cancel()
        }
    }
}
