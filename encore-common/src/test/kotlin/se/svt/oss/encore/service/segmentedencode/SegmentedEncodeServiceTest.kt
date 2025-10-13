// SPDX-FileCopyrightText: 2025 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.segmentedencode

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.SegmentedEncodingInfo
import se.svt.oss.encore.model.queue.TaskType
import se.svt.oss.encore.service.FfmpegExecutor
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File

class SegmentedEncodeServiceTest {

    private val ffmpegExecutor: FfmpegExecutor = mockk()
    private val mediaAnalyzerService: MediaAnalyzerService = mockk()
    private val service = SegmentedEncodeService(ffmpegExecutor, mediaAnalyzerService)

    private fun createJobWithSegmentedEncoding(
        numSegments: Int,
        segmentedAudioEncode: Boolean,
        outputFolder: String = "/output/path",
        baseName: String = "test",
    ) = defaultEncoreJob().copy(
        outputFolder = outputFolder,
        baseName = baseName,
        segmentedEncodingInfo = SegmentedEncodingInfo(
            segmentLength = 10.0,
            numSegments = numSegments,
            numTasks = if (segmentedAudioEncode) numSegments else numSegments + 1,
            segmentedAudioEncode = segmentedAudioEncode,
        ),
    )

    private fun createSegmentFiles(workDir: File, baseName: String, suffixes: List<String>, segmentCount: Int) {
        suffixes.forEach { suffix ->
            repeat(segmentCount) { i ->
                File(workDir, "${baseName}_${"%05d".format(i)}$suffix").writeText("segment$i")
            }
        }
    }

    private fun setupDirectories(tempDir: File): Pair<File, File> {
        val outputFolder = File(tempDir, "output").apply { mkdirs() }
        val workDir = File(tempDir, "work").apply { mkdirs() }
        return outputFolder to workDir
    }

    private fun assertOperationMatches(
        operation: SegmentedEncodeService.JoinSegmentOperation,
        expectedTarget: File,
        expectedSegments: List<File>,
        expectedAudio: File? = null,
    ) {
        assertEquals(expectedTarget, operation.target)
        assertEquals(expectedSegments.size, operation.segmentFiles.size)
        expectedSegments.forEachIndexed { index, expectedFile ->
            assertEquals(expectedFile, operation.segmentFiles[index])
        }
        if (expectedAudio != null) {
            assertEquals(expectedAudio, operation.audioFile)
        } else {
            assertNull(operation.audioFile)
        }
    }

    private fun expectedSegmentFiles(workDir: File, baseName: String, suffix: String, count: Int): List<File> =
        (0 until count).map { File(workDir, "${baseName}_${"%05d".format(it)}$suffix") }

    private fun createSegmentFilesList(workDir: File, baseName: String, suffix: String, count: Int): List<File> =
        expectedSegmentFiles(workDir, baseName, suffix, count).onEach { it.writeText("segment") }

    private fun expectedSegmentListContent(segments: List<File>): String =
        segments.joinToString("\n", postfix = "\n") { "file ${it.absolutePath}" }

    private fun assertSegmentListFileMatches(actual: File, expectedFile: File, expectedSegments: List<File>) {
        assertEquals(expectedFile, actual)
        assertEquals(true, actual.exists())
        assertEquals(expectedSegmentListContent(expectedSegments), actual.readText())
    }

    @Nested
    inner class CreateTasksTest {

        @ParameterizedTest(name = "numSegments={0}, segmentedAudio={1} creates {2} tasks")
        @CsvSource(
            "3, true, 3",
            "10, true, 10",
            "3, false, 4",
            "1, false, 2",
        )
        fun `creates correct number of tasks`(numSegments: Int, segmentedAudioEncode: Boolean, expectedTasks: Int) {
            val encoreJob = createJobWithSegmentedEncoding(numSegments, segmentedAudioEncode)

            val tasks = service.createTasks(encoreJob)

            assertEquals(expectedTasks, tasks.size)
        }

        @Test
        fun `creates audio-video segment tasks when audio is segmented`() {
            val encoreJob = createJobWithSegmentedEncoding(numSegments = 3, segmentedAudioEncode = true)

            val tasks = service.createTasks(encoreJob)

            tasks.forEachIndexed { index, task ->
                assertEquals(TaskType.AUDIOVIDEOSEGMENT, task.type)
                assertEquals(index, task.taskNo)
                assertEquals(index, task.segment)
            }
        }

        @Test
        fun `creates separate audio task and video segments when audio is not segmented`() {
            val encoreJob = createJobWithSegmentedEncoding(numSegments = 3, segmentedAudioEncode = false)

            val tasks = service.createTasks(encoreJob)

            assertEquals(TaskType.AUDIOFULL, tasks[0].type)
            assertEquals(0, tasks[0].taskNo)

            tasks.drop(1).forEachIndexed { index, task ->
                assertEquals(TaskType.VIDEOSEGMENT, task.type)
                assertEquals(index + 1, task.taskNo)
                assertEquals(index, task.segment)
            }
        }
    }

    @Nested
    inner class PrepareJoinSegmentTest {

        @TempDir
        lateinit var tempDir: File

        @Test
        fun `prepares join operations for segmented audio encode`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            createSegmentFiles(workDir, "test", listOf("_720p.mp4", "_1080p.mp4"), 3)

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 3,
                segmentedAudioEncode = true,
                outputFolder = outputFolder.absolutePath,
            )

            val operations = service.prepareJoinSegment(encoreJob, workDir)

            assertEquals(2, operations.size)
            assertOperationMatches(
                operations["test_720p.mp4"]!!,
                expectedTarget = File(outputFolder, "test_720p.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_720p.mp4", 3),
            )
            assertOperationMatches(
                operations["test_1080p.mp4"]!!,
                expectedTarget = File(outputFolder, "test_1080p.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_1080p.mp4", 3),
            )
        }

        @Test
        fun `prepares join operations for separate audio encode`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            createSegmentFiles(workDir, "test", listOf("_720p.mp4"), 2)
            File(audioDir, "test_720p.mp4").writeText("audio")
            File(audioDir, "test_audio.mp4").writeText("audio_only")

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 2,
                segmentedAudioEncode = false,
                outputFolder = outputFolder.absolutePath,
            )

            val operations = service.prepareJoinSegment(encoreJob, workDir)

            assertEquals(2, operations.size)
            assertOperationMatches(
                operations["test_720p.mp4"]!!,
                expectedTarget = File(outputFolder, "test_720p.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_720p.mp4", 2),
                expectedAudio = File(audioDir, "test_720p.mp4"),
            )
            assertOperationMatches(
                operations["test_audio.mp4"]!!,
                expectedTarget = File(outputFolder, "test_audio.mp4"),
                expectedSegments = emptyList(),
                expectedAudio = File(audioDir, "test_audio.mp4"),
            )
        }

        @Test
        fun `groups segment files by suffix correctly`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            createSegmentFiles(workDir, "test", listOf(".mp4", "_high.mp4", "_low.mp4"), 2)

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 2,
                segmentedAudioEncode = true,
                outputFolder = outputFolder.absolutePath,
            )

            val operations = service.prepareJoinSegment(encoreJob, workDir)

            assertEquals(3, operations.size)
            assertOperationMatches(
                operations["test.mp4"]!!,
                expectedTarget = File(outputFolder, "test.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", ".mp4", 2),
            )
            assertOperationMatches(
                operations["test_high.mp4"]!!,
                expectedTarget = File(outputFolder, "test_high.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_high.mp4", 2),
            )
            assertOperationMatches(
                operations["test_low.mp4"]!!,
                expectedTarget = File(outputFolder, "test_low.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_low.mp4", 2),
            )
        }
    }

    @Nested
    inner class JoinSegmentsTest {

        @TempDir
        lateinit var tempDir: File

        @Test
        fun `joins segments with audio file`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            val segments = createSegmentFilesList(workDir, "test", "_720p.mp4", 2)
            val audioFile = File(audioDir, "test_720p.mp4").apply { writeText("audio") }
            val targetFile = File(outputFolder, "test_720p.mp4")

            val segmentListFileSlot = slot<File>()
            val mockMediaFile = mockk<MediaFile>()
            every { ffmpegExecutor.joinSegments(any(), capture(segmentListFileSlot), any(), any()) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, segments, audioFile)
            val encoreJob = defaultEncoreJob().copy(outputFolder = outputFolder.absolutePath, baseName = "test")

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, audioFile) }
            assertSegmentListFileMatches(segmentListFileSlot.captured, File(workDir, "test_720p_filelist.txt"), segments)
        }

        @Test
        fun `joins segments without audio file`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)

            val segments = createSegmentFilesList(workDir, "test", "_720p.mp4", 2)
            val targetFile = File(outputFolder, "test_720p.mp4")

            val segmentListFileSlot = slot<File>()
            val mockMediaFile = mockk<MediaFile>()
            every { ffmpegExecutor.joinSegments(any(), capture(segmentListFileSlot), any(), null) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, segments, null)
            val encoreJob = defaultEncoreJob().copy(outputFolder = outputFolder.absolutePath, baseName = "test")

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, null) }
            assertSegmentListFileMatches(segmentListFileSlot.captured, File(workDir, "test_720p_filelist.txt"), segments)
        }

        @Test
        fun `copies audio file when no video segments exist`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            val audioFile = File(audioDir, "test_audio.mp4").apply { writeText("audio_only") }
            val targetFile = File(outputFolder, "test_audio.mp4")

            val mockMediaFile = mockk<MediaFile>()
            every { mediaAnalyzerService.analyze(targetFile.absolutePath) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, emptyList(), audioFile)
            val encoreJob = defaultEncoreJob().copy(outputFolder = outputFolder.absolutePath, baseName = "test")

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            assertEquals("audio_only", targetFile.readText())
            verify { mediaAnalyzerService.analyze(targetFile.absolutePath) }
        }
    }
}
