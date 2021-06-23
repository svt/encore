// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.awaitility.Awaitility.await
import org.awaitility.Durations
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import se.svt.oss.junit5.redis.EmbeddedRedisExtension
import se.svt.oss.randomportinitializer.RandomPortInitializer
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.callback.JobProgress
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ExtendWith(EmbeddedRedisExtension::class)
@ContextConfiguration(initializers = [RandomPortInitializer::class])
@DirtiesContext
class EncoreIntegrationTestBase() {

    @Autowired
    lateinit var encoreClient: EncoreClient

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var scheduler: ThreadPoolTaskScheduler

    @Autowired
    lateinit var encoreProperties: EncoreProperties

    @Value("classpath:input/test.mp4")
    lateinit var testFileSurround: Resource

    @Value("classpath:input/test_stereo.mp4")
    lateinit var testFileStereo: Resource

    @Value("classpath:input/multiple_video.mp4")
    lateinit var testFileMultipleVideo: Resource

    @Value("classpath:input/multiple_audio.mp4")
    lateinit var testFileMultipleAudio: Resource

    lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.setDispatcher(
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return MockResponse()
                }
            }
        )
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    fun successfulTest(
        job: EncoreJob,
        expectedOutputFiles: List<String>,
    ): EncoreJob {
        val createdJob = createAndAwaitJob(
            job = job,
            timeout = Durations.FIVE_MINUTES
        ) { it.status.isCompleted }

        assertThat(createdJob).hasStatus(Status.SUCCESSFUL)

        val requestCount = mockServer.requestCount
        assertThat(requestCount).isGreaterThan(0)

        val jobList = mutableListOf<JobProgress>()
        repeat(requestCount) {
            val request = mockServer.takeRequest()
            val json = request.body.readUtf8()
            val progress = objectMapper.readValue<JobProgress>(json)
            jobList.add(progress)
            assertThat(progress).hasJobId(createdJob.id).hasExternalId(createdJob.externalId)
            assertThat(progress.progress).isBetween(0, 100)
        }
        assertThat(jobList.subList(0, jobList.size - 1)).allMatch { it.status == Status.IN_PROGRESS }
        assertThat(jobList.last()).hasProgress(100).hasStatus(Status.SUCCESSFUL)

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
        condition: (EncoreJob) -> Boolean
    ): EncoreJob =
        awaitJob(
            jobId = encoreClient.createJob(job).id,
            pollInterval = pollInterval,
            timeout = timeout,
            condition = condition
        )

    fun awaitJob(
        jobId: UUID,
        pollInterval: Duration = Durations.TWO_SECONDS,
        timeout: Duration = Durations.ONE_MINUTE,
        condition: (EncoreJob) -> Boolean
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
        file: Resource = testFileSurround
    ) =
        EncoreJob(
            externalId = "externalId",
            baseName = file.file.nameWithoutExtension,
            profile = "program",
            outputFolder = outputDir.absolutePath,
            progressCallbackUri = URI.create("http://localhost:${mockServer.port}/callbacks/111"),
            debugOverlay = true,
            priority = priority,
            inputs = listOf(
                AudioVideoInput(
                    uri = file.file.absolutePath,
                    useFirstAudioStreams = 6
                )
            ),
            logContext = mapOf("FlowId" to UUID.randomUUID().toString())
        )

    fun defaultExpectedOutputFiles(outputDir: File, testFile: Resource): List<String> {
        return listOf(
            expectedFile(outputDir, testFile, "x264_3100.mp4"),
            expectedFile(outputDir, testFile, "x264_2069.mp4"),
            expectedFile(outputDir, testFile, "x264_1312.mp4"),
            expectedFile(outputDir, testFile, "x264_806.mp4"),
            expectedFile(outputDir, testFile, "x264_324.mp4"),
            expectedFile(outputDir, testFile, "STEREO.mp4"),
            expectedFile(outputDir, testFile, "thumb01.jpg"),
            expectedFile(outputDir, testFile, "thumb02.jpg"),
            expectedFile(outputDir, testFile, "thumb03.jpg"),
            expectedFile(outputDir, testFile, "12x20_160x90_thumbnail_map.jpg")
        )
    }

    fun expectedFile(outputDir: File, baseName: String, suffix: String) =
        "${outputDir.absolutePath}/${baseName}_$suffix"

    fun expectedFile(outputDir: File, testFile: Resource, suffix: String) =
        expectedFile(outputDir, testFile.file.nameWithoutExtension, suffix)
}
