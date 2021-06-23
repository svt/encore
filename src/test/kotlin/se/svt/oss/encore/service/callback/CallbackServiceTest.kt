// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.callback

import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.callback.JobProgress
import java.net.URI

@ExtendWith(MockKExtension::class)
class CallbackServiceTest {

    @MockK
    private lateinit var callackClient: CallbackClient

    @InjectMockKs
    private lateinit var callbackService: CallbackService

    private val encoreJob = EncoreJob(
        outputFolder = "/some/output",
        profile = "program",
        progressCallbackUri = URI.create("wwww.callback.com"),
        progress = 50,
        baseName = "file"
    )

    private val progress = JobProgress(
        encoreJob.id,
        encoreJob.externalId,
        encoreJob.progress,
        encoreJob.status
    )

    @Test
    fun `successful callback`() {
        every { callackClient.sendProgressCallback(encoreJob.progressCallbackUri!!, progress) } just Runs

        callbackService.sendProgressCallback(encoreJob)

        verify { callackClient.sendProgressCallback(encoreJob.progressCallbackUri!!, progress) }
    }

    @Test
    fun `some error upon callback`() {
        every {
            callackClient.sendProgressCallback(
                encoreJob.progressCallbackUri!!,
                progress
            )
        } throws Exception("error")

        callbackService.sendProgressCallback(encoreJob)

        verify { callackClient.sendProgressCallback(encoreJob.progressCallbackUri!!, progress) }
    }
}
