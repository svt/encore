// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import org.springframework.hateoas.LinkRelation
import org.springframework.hateoas.server.LinkRelationProvider
import org.springframework.stereotype.Component
import se.svt.oss.encore.model.EncoreJob

@Component
class EncoreJobLinkRelationProvider : LinkRelationProvider {
    override fun getItemResourceRelFor(type: Class<*>): LinkRelation = LinkRelation.of("encoreJob")

    override fun getCollectionResourceRelFor(type: Class<*>): LinkRelation = LinkRelation.of("encoreJobs")

    override fun supports(delimiter: LinkRelationProvider.LookupContext): Boolean = delimiter.type == EncoreJob::class.java
}
