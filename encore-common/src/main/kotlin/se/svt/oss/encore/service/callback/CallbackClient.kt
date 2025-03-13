// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.callback

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import se.svt.oss.encore.model.callback.JobProgress
import java.net.URI

@HttpExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
interface CallbackClient {

    @PostExchange
    fun sendProgressCallback(callbackUri: URI, @RequestBody progress: JobProgress)
}
