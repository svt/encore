// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.cancellation

import java.util.UUID
import kotlinx.coroutines.Job
import mu.KotlinLogging
import org.redisson.api.listener.MessageListener
import se.svt.oss.encore.model.CancelEvent

class CancellationListener(
    private val encoreJobId: UUID,
    private val coroutineJob: Job
) : MessageListener<CancelEvent> {

    private val log = KotlinLogging.logger {}

    override fun onMessage(channel: CharSequence?, msg: CancelEvent?) {
        val jobId = msg?.jobId
        if (jobId == encoreJobId) {
            log.info { "Received cancel event for job $jobId" }
            coroutineJob.cancel()
        }
    }
}
