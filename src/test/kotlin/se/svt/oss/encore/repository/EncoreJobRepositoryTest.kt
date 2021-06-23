// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import se.svt.oss.junit5.redis.EmbeddedRedisExtension
import se.svt.oss.randomportinitializer.RandomPortInitializer
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(EmbeddedRedisExtension::class)
@ActiveProfiles("test")
@ContextConfiguration(initializers = [RandomPortInitializer::class])
@DirtiesContext
class EncoreJobRepositoryTest {

    @Autowired
    lateinit var repository: EncoreJobRepository

    @Test
    fun filteredTest() {
        createAndSaveJob("http://transcoder1", Status.FAILED)
        createAndSaveJob("http://transcoder2", Status.QUEUED)
        createAndSaveJob("http://transcoder3", Status.QUEUED)

        val findByStatus = repository.findByStatus(Status.QUEUED, PageRequest.of(0, 10))
        assertThat(findByStatus.totalElements).isEqualTo(2)
        val callbackUrls = findByStatus.map { it.progressCallbackUri }
        assertThat(callbackUrls).containsExactlyInAnyOrder(
            URI.create("http://transcoder2"),
            URI.create("http://transcoder3")
        )
        repository.deleteAll()
    }

    private fun createAndSaveJob(url: String, status: Status) {
        val encoreJob = EncoreJob(
            id = UUID.randomUUID(),
            externalId = "externalId",
            profile = "animerat",
            outputFolder = "/shares/test",
            createdDate = OffsetDateTime.now(),
            progressCallbackUri = URI.create(url),
            baseName = "test"
        )
        encoreJob.status = status
        encoreJob.startedDate = OffsetDateTime.now()
        encoreJob.completedDate = OffsetDateTime.now()
        repository.save(encoreJob)
    }
}
