// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.awaitility.Durations
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.callback.JobProgress
import se.svt.oss.encore.model.input.AudioVideoInput
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.io.File
import java.nio.file.Paths

@ExtendWith(S3StorageExtension::class)
abstract class EncoreS3IntegrationTest(wireMockRuntimeInfo: WireMockRuntimeInfo) : EncoreIntegrationTestBase(wireMockRuntimeInfo) {
    private val log = KotlinLogging.logger {}

    @Autowired
    lateinit var s3Client: S3AsyncClient

    val inputBucket = "input-bucket"
    val outputBucket = "output-bucket"

    @BeforeEach
    override fun setUp() {
        super.setUp()

        listOf(inputBucket, outputBucket).forEach { bucket ->
            s3Client.createBucket { it.bucket(bucket) }
                .get()
        }
    }

    @AfterEach
    fun tearDown() {
        listOf(inputBucket, outputBucket).forEach { bucket ->
            s3Client.listObjects { it.bucket(bucket) }
                .get()
                .contents()
                .forEach { obj ->
                    s3Client.deleteObject { it.bucket(bucket).key(obj.key()) }
                        .get()
                }
            s3Client.deleteBucket { it.bucket(bucket) }
                .get()
        }
    }

    fun jobWiths3InputAndOutputIsSuccessful(@TempDir outputDir: File) {
        val filename = "test.mp4"
        val remoteInput = uploadInputfile(testFileSurround.file.absolutePath, filename)

        val job = job(outputDir = outputDir, file = testFileSurround)
            .copy(
                outputFolder = "s3://$outputBucket/output/",
                inputs = listOf(AudioVideoInput(uri = remoteInput)),
            )

        val createdJob = createAndAwaitJob(
            job = job,
            timeout = Durations.FIVE_MINUTES,
        ) { it.status.isCompleted }

        assertThat(createdJob).hasStatus(Status.SUCCESSFUL)

        val progressCalls = wireMockRuntimeInfo
            .wireMock
            .serveEvents.map { objectMapper.readValue<JobProgress>(it.request.bodyAsString) }
        assertThat(progressCalls.first())
            .hasStatus(Status.SUCCESSFUL)

        val expectedFiles = (defaultExpectedOutputFileSuffixes() + listOf("SURROUND.mp4"))
            .map { "output/${createdJob.baseName}_$it" }

        val actualFiles = s3Client.listObjectsV2 {
            it.bucket(outputBucket)
                .prefix("output/")
        }
            .get()
            .contents()
            .map { it.key() ?: "" }
        assertThat(actualFiles).containsExactlyInAnyOrder(*expectedFiles.toTypedArray())
        // expectedFiles.forEach { minioClient.statObject(StatObjectArgs.builder().bucket(outputBucket).`object`(it).build()) }
    }

    private fun uploadInputfile(localPath: String, key: String): String {
        s3Client.putObject({ it.bucket(inputBucket).key(key).build() }, Paths.get(localPath))

        return "s3://$inputBucket/$key"
    }

    @Nested
    @ActiveProfiles(profiles = ["test-local", "test-s3"])
    @WireMockTest
    class StandardS3Access(wireMockRuntimeInfo: WireMockRuntimeInfo) : EncoreS3IntegrationTest(wireMockRuntimeInfo) {
        @Test
        fun jobWithS3InputAndOutputIsSuccessful(@TempDir outputDir: File) {
            super.jobWiths3InputAndOutputIsSuccessful(outputDir)
        }
    }

    @Nested
    @ActiveProfiles(profiles = ["test-local", "test-s3"])
    @TestPropertySource(
        properties = [
            "remote-files.s3.anonymous-access=true",
            "remote-files.s3.use-path-style=true", // localstack requires path style access
        ],
    )
    @WireMockTest
    class AnonymousS3Access(wireMockRuntimeInfo: WireMockRuntimeInfo) : EncoreS3IntegrationTest(wireMockRuntimeInfo) {
        @Test
        fun jobWithS3InputAndOutputIsSuccessful(@TempDir outputDir: File) {
            super.jobWiths3InputAndOutputIsSuccessful(outputDir)
        }
    }
}
