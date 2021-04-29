// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.callback

import mu.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.callback.JobProgress

@Service
class CallbackService(private val callbackClient: CallbackClient) {

    private val log = KotlinLogging.logger {}

    fun sendProgressCallback(encoreJob: EncoreJob) {
        encoreJob.progressCallbackUri?.let {
            try {
                callbackClient.sendProgressCallback(
                    it,
                    JobProgress(
                        encoreJob.id,
                        encoreJob.externalId,
                        encoreJob.progress,
                        encoreJob.status
                    )
                )
            } catch (e: Exception) {
                log.debug(e) { "Sending progress callback failed" }
            }
        }
    }
}
