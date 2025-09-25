// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import org.awaitility.Awaitility.await
import org.awaitility.Durations
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.Resource
import org.springframework.test.annotation.DirtiesContext
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.callback.JobProgress
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.profile.ChannelLayout
import java.io.File
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(RedisExtension::class)
@DirtiesContext
@Import(TestConfig::class)
class EncoreIntegrationTestBase(val wireMockRuntimeInfo: WireMockRuntimeInfo) {

    @Autowired
    lateinit var encoreClient: EncoreClient

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Value("classpath:input/test.mp4")
    lateinit var testFileSurround: Resource

    @Value("classpath:input/test_stereo.mp4")
    lateinit var testFileStereo: Resource

    @Value("classpath:input/multiple_video.mp4")
    lateinit var testFileMultipleVideo: Resource

    @Value("classpath:input/multiple_audio.mp4")
    lateinit var testFileMultipleAudio: Resource

    @BeforeEach
    fun setUp() {
        stubFor(post(anyUrl()).willReturn(ok()))
    }

    fun successfulTest(
        job: EncoreJob,
        expectedOutputFiles: List<String>,
    ): EncoreJob {
        val createdJob = createAndAwaitJob(
            job = job,
            timeout = Durations.FIVE_MINUTES,
        ) { it.status.isCompleted }

        assertThat(createdJob).hasStatus(Status.SUCCESSFUL)

        val progressCalls =
            wireMockRuntimeInfo
                .wireMock
                .serveEvents.map { objectMapper.readValue<JobProgress>(it.request.bodyAsString) }
        assertThat(progressCalls.first())
            .hasStatus(Status.SUCCESSFUL)

        val output = createdJob.output.map { it.file }
        assertThat(output).containsExactlyInAnyOrder(*expectedOutputFiles.toTypedArray())

        expectedOutputFiles
            .map { File(it) }
            .forEach { assertThat(it).isNotEmpty }

        return createdJob
    }

    fun createAndAwaitJob(
        job: EncoreJob,
        pollInterval: Duration = Durations.TWO_SECONDS,
        timeout: Duration = Durations.ONE_MINUTE,
        condition: (EncoreJob) -> Boolean,
    ): EncoreJob =
        awaitJob(
            jobId = encoreClient.createJob(job).id,
            pollInterval = pollInterval,
            timeout = timeout,
            condition = condition,
        )

    fun awaitJob(
        jobId: UUID,
        pollInterval: Duration = Durations.TWO_SECONDS,
        timeout: Duration = Durations.ONE_MINUTE,
        condition: (EncoreJob) -> Boolean,
    ): EncoreJob {
        lateinit var job: EncoreJob
        await().pollInterval(pollInterval)
            .atMost(timeout)
            .until {
                job = encoreClient.getJob(jobId)
                condition(job)
            }
        return job
    }

    fun job(
        outputDir: File,
        priority: Int = 0,
        file: Resource = testFileSurround,
    ) =
        EncoreJob(
            externalId = "externalId",
            baseName = file.file.nameWithoutExtension,
            profile = "program",
            outputFolder = outputDir.absolutePath,
            progressCallbackUri = "http://localhost:${wireMockRuntimeInfo.httpPort}/callbacks/111",
            debugOverlay = true,
            priority = priority,
            inputs = listOf(
                AudioVideoInput(
                    uri = file.file.absolutePath,
                    channelLayout = ChannelLayout.CH_LAYOUT_5POINT1,
                ),
            ),
            logContext = mapOf("FlowId" to UUID.randomUUID().toString()),
        )

    fun defaultExpectedOutputFiles(outputDir: File, testFile: Resource): List<String> = listOf(
        expectedFile(outputDir, testFile, "x264_3100.mp4"),
        expectedFile(outputDir, testFile, "x264_2069.mp4"),
        expectedFile(outputDir, testFile, "x264_1312.mp4"),
        expectedFile(outputDir, testFile, "x264_806.mp4"),
        expectedFile(outputDir, testFile, "x264_324.mp4"),
        expectedFile(outputDir, testFile, "STEREO.mp4"),
        expectedFile(outputDir, testFile, "thumb01.jpg"),
        expectedFile(outputDir, testFile, "thumb02.jpg"),
        expectedFile(outputDir, testFile, "thumb03.jpg"),
        expectedFile(outputDir, testFile, "12x20_160x90_thumbnail_map.jpg"),
    )

    fun expectedFile(outputDir: File, baseName: String, suffix: String) =
        "${outputDir.absolutePath}/${baseName}_$suffix"

    fun expectedFile(outputDir: File, testFile: Resource, suffix: String) =
        expectedFile(outputDir, testFile.file.nameWithoutExtension, suffix)
}
