// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.callback

import java.net.URI
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import se.svt.oss.encore.model.callback.JobProgress

@FeignClient("callback")
interface CallbackClient {

    @PostMapping
    fun sendProgressCallback(callbackUri: URI, progress: JobProgress)
}
