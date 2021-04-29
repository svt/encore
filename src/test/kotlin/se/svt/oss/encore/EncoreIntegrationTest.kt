// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.module.kotlin.readValue
import org.awaitility.Awaitility.await
import org.awaitility.Durations
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import se.svt.oss.redisson.starter.queue.QueueItem
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.callback.JobProgress
import java.io.File
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
class EncoreIntegrationTest : EncoreIntegrationTestBase() {

    @Test
    fun jobIsSuccessfulSurround(@TempDir outputDir: File) {
        successfulTest(
            outputDir,
            defaultExpectedOutputFiles(outputDir, testFileSurround) +
                listOf(
                    expectedFile(outputDir, testFileSurround, "STEREO_DE.mp4"),
                    expectedFile(outputDir, testFileSurround, "SURROUND.mp4")
                ),
            testFileSurround,
            6
        )
    }

    @Test
    fun jobIsSuccessfulStereo(@TempDir outputDir: File) {
        successfulTest(outputDir, defaultExpectedOutputFiles(outputDir, testFileStereo), testFileStereo, 1)
    }

    @Test
    @Disabled("Flaky")
    fun highPriorityJobIsRunInParallel(@TempDir outputDir1: File, @TempDir outputDir2: File) {
        val standardPriorityJob = createAndAwaitJob(
            job(
                outputDir = outputDir1,
                priority = 0,
            )
        ) { it.status == Status.IN_PROGRESS }

        val highPriorityJob = createAndAwaitJob(
            job(
                outputDir = outputDir2,
                priority = 100
            )
        ) { it.status == Status.IN_PROGRESS }

        encoreClient.cancel(standardPriorityJob.id)
        awaitJob(standardPriorityJob.id) { it.status.isCompleted }

        encoreClient.cancel(highPriorityJob.id)
        awaitJob(highPriorityJob.id) { it.status.isCompleted }
    }

    @Test
    fun jobIsCancelled(@TempDir outputDir: File) {
        var createdJob = encoreClient.createJob(job(outputDir))
        await().pollInterval(1, TimeUnit.SECONDS)
            .atMost(Durations.ONE_MINUTE)
            .until { mockServer.requestCount > 0 }

        encoreClient.cancel(createdJob.id)

        createdJob = awaitJob(
            jobId = createdJob.id,
            pollInterval = Durations.ONE_SECOND,
            timeout = Durations.ONE_MINUTE
        ) { it.status.isCompleted }

        assertThat(createdJob)
            .hasStatus(Status.CANCELLED)

        val requestCount = mockServer.requestCount
        assertThat(requestCount).isGreaterThan(0)

        val jobList = mutableListOf<JobProgress>()
        repeat(requestCount) {
            val request = mockServer.takeRequest()
            val json = request.body.readUtf8()
            val progress = objectMapper.readValue<JobProgress>(json)
            jobList.add(progress)
            assertThat(progress).hasJobId(createdJob.id).hasExternalId(createdJob.externalId)
            assertThat(progress.progress).isGreaterThan(0)
        }
        assertThat(jobList.subList(0, jobList.size - 1)).allMatch { it.status == Status.IN_PROGRESS }
        assertThat(jobList.last().progress).isLessThan(100)
        assertThat(jobList.last()).hasStatus(Status.CANCELLED)
    }

    @Test
    fun jobIsFailedOnIncompatible(@TempDir outputDir: File) {
        val createdJob = createAndAwaitJob(
            job = job(outputDir).copy(profile = "dpb_size_failed"),
            pollInterval = Durations.ONE_SECOND,
            timeout = Durations.ONE_MINUTE
        ) { it.status.isCompleted }

        assertThat(createdJob)
            .hasStatus(Status.FAILED)

        assertThat(createdJob.message)
            .contains("Coding might not be compatible on all devices")
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun jobIsInterrupted(@TempDir outputDir: File) {
        var createdJob = createAndAwaitJob(
            job = job(outputDir),
            timeout = Durations.ONE_MINUTE
        ) { it.status == Status.IN_PROGRESS }

        scheduler.shutdown()

        createdJob = awaitJob(
            jobId = createdJob.id,
            timeout = Durations.TEN_SECONDS
        ) { it.status == Status.QUEUED }

        var queueItem: QueueItem? = null
        await().pollInterval(2, TimeUnit.SECONDS)
            .atMost(Durations.TEN_SECONDS)
            .until {
                queueItem = encoreClient.queue().firstOrNull()
                queueItem != null
            }

        assertThat(queueItem)
            .hasId(createdJob.id.toString())
            .hasPriority(createdJob.priority)
    }
}
