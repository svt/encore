// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.data.web.PagedResourcesAssembler
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.PagedModel
import org.springframework.hateoas.RepresentationModel
import org.springframework.hateoas.server.EntityLinks
import org.springframework.hateoas.server.ExposesResourceFor
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.redis.RedisService
import se.svt.oss.encore.service.callback.CallbackService
import se.svt.oss.encore.service.queue.QueueService
import java.util.UUID

private val log = KotlinLogging.logger { }

@CrossOrigin
@RestController
@ExposesResourceFor(EncoreJob::class)
@RequestMapping("/encoreJobs")
class EncoreController(
    private val pagedAssembler: PagedResourcesAssembler<EncoreJob>,
    private val redisService: RedisService,
    entityLinks: EntityLinks,
    private val queueService: QueueService,
    private val callbackService: CallbackService,
) {

    private val modelAssembler = ModelAssembler(entityLinks)

    @Operation(summary = "Create a new job", tags = ["encoreJobs"])
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJob(
        @Valid @RequestBody encoreJob: EncoreJob,
        authentication: Authentication?,
        @RequestHeader(HttpHeaders.USER_AGENT) userAgent: String?,
    ): EntityModel<EncoreJob> {
        withLoggingContext(encoreJob.contextMap) {
            log.info { "Job created by user=${authentication?.name ?: "anonymous"} userAgent=$userAgent" }
            encoreJob.updateStatus(Status.QUEUED)
            redisService.save(encoreJob)
            queueService.enqueue(encoreJob)
            return modelAssembler.toModel(encoreJob)
        }
    }

    @Operation(summary = "Get an encoreJob by id", tags = ["encoreJobs"])
    @GetMapping("/{id}")
    fun getJob(@PathVariable id: UUID): EntityModel<EncoreJob> =
        redisService.findByIdOrNull(id)
            ?.let { modelAssembler.toModel(it) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job with id $id not found")

    @Operation(summary = "Get all EncoreJobs", tags = ["encoreJobs"])
    @GetMapping
    fun getJobs(
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdDate"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): PagedModel<EntityModel<EncoreJob>> {
        val entityModels = pagedAssembler.toModel(
            redisService.findAll(pageable),
            modelAssembler,
        )
        entityModels.add(linkTo<EncoreController> { listSearches() }.withRel("search"))
        return entityModels
    }

    @Operation(summary = "Find EncoreJobs by status", tags = ["encoreJobs"])
    @GetMapping("/search/findByStatus")
    fun findByStatus(
        @RequestParam("status")
        status: Status,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdDate"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): PagedModel<EntityModel<EncoreJob>> =
        pagedAssembler.toModel(
            redisService.findByStatus(status, pageable),
            modelAssembler,
        )

    @Operation(summary = "Find EncoreJobs by externalId", tags = ["encoreJobs"])
    @GetMapping("/search/findByExternalId")
    fun findByExternalId(
        @RequestParam("externalId")
        externalId: String,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdDate"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): PagedModel<EntityModel<EncoreJob>> =
        pagedAssembler.toModel(
            redisService.findByExternalId(externalId, pageable),
            modelAssembler,
        )

    @Operation(summary = "Find EncoreJobs by profile", tags = ["encoreJobs"])
    @GetMapping("/search/findByProfile")
    fun findByProfile(
        @RequestParam("profile")
        profile: String,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdDate"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): PagedModel<EntityModel<EncoreJob>> =
        pagedAssembler.toModel(
            redisService.findByProfile(profile, pageable),
            modelAssembler,
        )

    @Operation(summary = "Find EncoreJobs by baseName", tags = ["encoreJobs"])
    @GetMapping("/search/findByBaseName")
    fun findByBaseName(
        @RequestParam("baseName")
        baseName: String,
        @ParameterObject
        @PageableDefault(size = 10, sort = ["createdDate"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): PagedModel<EntityModel<EncoreJob>> =
        pagedAssembler.toModel(
            redisService.findByBaseName(baseName, pageable),
            modelAssembler,
        )

    @Operation(hidden = true)
    @GetMapping("/search")
    fun listSearches(): RepresentationModel<*> =
        RepresentationModel.of(
            null,
            listOf(
                Link.of(
                    ServletUriComponentsBuilder.fromCurrentRequest().path("/findByStatus{?status}").build()
                        .toUriString(),
                ).withRel("findByStatus"),
                Link.of(
                    ServletUriComponentsBuilder.fromCurrentRequest().path("/findByProfile{?profile}").build()
                        .toUriString(),
                ).withRel("findByProfile"),
                Link.of(
                    ServletUriComponentsBuilder.fromCurrentRequest().path("/findByBaseName{?baseName}").build()
                        .toUriString(),
                ).withRel("findByBaseName"),
                Link.of(
                    ServletUriComponentsBuilder.fromCurrentRequest().path("/findByExternalId{?externalId}").build()
                        .toUriString(),
                ).withRel("findByExternalId"),
                Link.of(ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString()),
            ),
        )

    @Operation(
        summary = "Cancel an EncoreJob",
        description = "Cancels an EncoreJob with the given JobId",
        tags = ["encoreJobs"],
    )
    @PostMapping("/{jobId}/cancel")
    fun cancel(
        @PathVariable jobId: UUID,
        authentication: Authentication?,
        @RequestHeader(HttpHeaders.USER_AGENT) userAgent: String?,
    ) {
        val job = redisService.findByIdOrNull(jobId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Job $jobId does not exist!")

        withLoggingContext(job.contextMap) {
            log.info { "Cancel requested for job $jobId by user=${authentication?.name ?: "anonymous"} userAgent=$userAgent" }
            when (job.status) {
                Status.NEW, Status.QUEUED -> {
                    job.updateStatus(Status.CANCELLED)
                    redisService.save(job)
                    if (redisService.cancel(jobId) < 1) {
                        callbackService.sendProgressCallback(job)
                    }
                }

                Status.IN_PROGRESS -> {
                    redisService.cancel(jobId)
                }

                else -> {
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Cannot cancel job with status ${job.status}!",
                    )
                }
            }
        }
    }
}
