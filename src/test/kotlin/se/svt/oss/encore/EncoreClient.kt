// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import java.util.UUID
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Pageable
import org.springframework.hateoas.PagedModel
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.queue.QueueItem

@FeignClient("encore", url = "http://localhost:\${server.port}")
interface EncoreClient {

    @GetMapping("/encoreJobs")
    fun jobs(): PagedModel<EncoreJob>

    @GetMapping("/encoreJobs/search/findByStatus")
    fun findByStatus(@RequestParam("status") status: Status, pageable: Pageable): PagedModel<EncoreJob>

    @PostMapping("/encoreJobs/{jobId}/cancel")
    fun cancel(@PathVariable("jobId") jobId: UUID)

    @PostMapping(
        "/encoreJobs",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun createJob(jobRequest: EncoreJob): EncoreJob

    @GetMapping("/health")
    fun health(): String

    @PostMapping(
        "/encoreJobs",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun postJson(json: String): EncoreJob

    @GetMapping("/encoreJobs/{jobId}")
    fun getJob(@PathVariable("jobId") jobId: UUID): EncoreJob

    @GetMapping("/queue")
    fun queue(): List<QueueItem>
}
