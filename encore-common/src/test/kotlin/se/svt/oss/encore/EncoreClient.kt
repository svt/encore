// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.data.domain.Pageable
import org.springframework.hateoas.PagedModel
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem
import java.util.UUID

@HttpExchange(accept = [MediaType.APPLICATION_JSON_VALUE], contentType = MediaType.APPLICATION_JSON_VALUE)
interface EncoreClient {

    @GetExchange("/encoreJobs")
    fun jobs(): PagedModel<EncoreJob>

    @GetExchange("/encoreJobs/search/findByStatus")
    fun findByStatus(@RequestParam("status") status: Status, pageable: Pageable): PagedModel<EncoreJob>

    @PostExchange("/encoreJobs/{jobId}/cancel")
    fun cancel(@PathVariable("jobId") jobId: UUID)

    @PostExchange(
        "/encoreJobs"
    )
    fun createJob(@RequestBody jobRequest: EncoreJob): EncoreJob

    @GetExchange("/health")
    fun health(): String

    @PostExchange(
        "/encoreJobs"
    )
    fun postJson(@RequestBody json: String): EncoreJob

    @GetExchange("/encoreJobs/{jobId}")
    fun getJob(@PathVariable("jobId") jobId: UUID): EncoreJob

    @GetExchange("/queue")
    fun queue(): List<QueueItem>
}
