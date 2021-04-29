// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status

@RepositoryRestResource
@Tag(name = "encorejob")
interface EncoreJobRepository : PagingAndSortingRepository<EncoreJob, UUID> {

    @Operation(summary = "Find EncoreJobs By Status", description = "Returns EncoreJobs according to the given Status")
    fun findByStatus(status: Status, pageable: Pageable): Page<EncoreJob>
}
