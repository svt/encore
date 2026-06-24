// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.server.EntityLinks
import org.springframework.hateoas.server.RepresentationModelAssembler
import se.svt.oss.encore.model.EncoreJob
import java.util.UUID

class ModelAssembler(private val entityLinks: EntityLinks) : RepresentationModelAssembler<EncoreJob, EntityModel<EncoreJob>> {
    override fun toModel(entity: EncoreJob): EntityModel<EncoreJob> = EntityModel.of(entity, selfLink(entity.id)) // , cancelLink(entity.id))
    private fun selfLink(id: UUID): Link =
        entityLinks.linkToItemResource(EncoreJob::class.java, id).withSelfRel()
}
