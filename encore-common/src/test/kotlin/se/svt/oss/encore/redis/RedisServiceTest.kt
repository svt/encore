// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.redis

import io.lettuce.core.FlushMode
import io.lettuce.core.api.StatefulRedisConnection
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.RedisExtension
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.input.AudioVideoInput
import java.util.UUID

@SpringBootTest
@ExtendWith(RedisExtension::class)
@ActiveProfiles("test")
class RedisServiceTest {

    @Autowired
    lateinit var redisService: RedisService

    @Autowired
    lateinit var redisConnection: StatefulRedisConnection<String, String>

    @AfterEach
    fun tearDown() {
        redisConnection.sync().flushdb(FlushMode.SYNC)
        redisService.createIndex()
    }

    private fun job(
        profile: String = "test",
        priority: Int = 0,
        baseName: String = "TEST_FILE",
        externalId: String = UUID.randomUUID().toString(),
    ) = EncoreJob(
        externalId = externalId,
        profile = profile,
        outputFolder = "/output/path",
        priority = priority,
        baseName = baseName,
        inputs = listOf(
            AudioVideoInput(
                uri = "/input/test.mp4",
            ),
        ),
    )

    @Test
    fun `save and findByIdOrNull roundtrip`() {
        val job = job()
        redisService.save(job)

        val found = redisService.findByIdOrNull(job.id)

        assertThat(found)
            .isNotNull
            .hasId(job.id)
            .hasProfile("test")
    }

    @Test
    fun `findByIdOrNull returns null for unknown id`() {
        assertThat(redisService.findByIdOrNull(java.util.UUID.randomUUID())).isNull()
    }

    @Test
    fun `updateProgress updates progress field`() {
        val job = job()
        redisService.save(job)

        redisService.updateProgress(job.id, 42)

        assertThat(redisService.findByIdOrNull(job.id)).hasProgress(42)
    }

    @Test
    fun `findAll returns saved jobs`() {
        val job1 = job(profile = "profile-a")
        val job2 = job(profile = "profile-b")
        redisService.save(job1)
        redisService.save(job2)

        val page = redisService.findAll(PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"))))

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.id }).containsExactly(job2.id, job1.id)
    }

    @Test
    fun `findByStatus returns only jobs with matching status`() {
        val queued = job().apply { status = Status.QUEUED }
        val successful = job().apply { status = Status.SUCCESSFUL }
        redisService.save(queued)
        redisService.save(successful)

        val page = redisService.findByStatus(Status.QUEUED, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"))))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.first()).hasId(queued.id)
    }

    @Test
    fun `findByProfile filters by profile`() {
        redisService.save(job(profile = "alpha"))
        redisService.save(job(profile = "beta"))

        val page = redisService.findByProfile("alpha", PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"))))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.first()).hasProfile("alpha")
    }

    @Test
    fun `findByExternalId filters by externalId`() {
        val job1 = job()
        val job2 = job()
        redisService.save(job1)
        redisService.save(job2)

        val page = redisService.findByExternalId(job1.externalId!!, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"))))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.first()).hasExternalId(job1.externalId)
    }

    @Test
    fun `findByBaseName filters by baseName`() {
        redisService.save(job(baseName = "FILE_A"))
        redisService.save(job(baseName = "FILE_B"))

        val page = redisService.findByBaseName("FILE_A", PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"))))

        assertThat(page.totalElements).isEqualTo(1)
        assertThat(page.content.first()).hasBaseName("FILE_A")
    }

    @Test
    fun `findAll throws InvalidQueryException for unsupported sort field`() {
        assertThatThrownBy {
            redisService.findAll(PageRequest.of(0, 10, Sort.by("unsupportedField")))
        }.isInstanceOf(InvalidQueryException::class.java)
            .hasMessageContaining("unsupportedField")
    }

    @Test
    fun `findAll throws InvalidQueryException for multiple sort orders`() {
        assertThatThrownBy {
            redisService.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdDate"), Sort.Order.asc("priority"))),
            )
        }.isInstanceOf(InvalidQueryException::class.java)
            .hasMessageContaining("Only one sort property is allowed")
    }
}
