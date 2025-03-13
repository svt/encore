// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.callback

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.callback.JobProgress
import java.net.URI

private val log = KotlinLogging.logger {}

@Service
class CallbackService(private val callbackClient: CallbackClient) {

    fun sendProgressCallback(encoreJob: EncoreJob) {
        encoreJob.progressCallbackUri?.let {
            try {
                callbackClient.sendProgressCallback(
                    URI.create(it),
                    JobProgress(
                        encoreJob.id,
                        encoreJob.externalId,
                        encoreJob.progress,
                        encoreJob.status,
                    ),
                )
            } catch (e: Exception) {
                log.debug(e) { "Sending progress callback failed" }
            }
        }
    }
}
