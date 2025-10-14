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
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.AudioEncodingMode
import se.svt.oss.encore.model.SegmentedEncodingInfo
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.profile.SimpleAudioEncode
import se.svt.oss.encore.model.profile.X264Encode
import se.svt.oss.encore.model.queue.TaskType
import se.svt.oss.encore.service.FfmpegExecutor
import se.svt.oss.encore.service.mediaanalyzer.MediaAnalyzerService
import se.svt.oss.encore.service.profile.ProfileService
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File

class SegmentedEncodeServiceTest {

    private val ffmpegExecutor: FfmpegExecutor = mockk()
    private val mediaAnalyzerService: MediaAnalyzerService = mockk()
    private val profileService: ProfileService = mockk()
    private val encoreProperties: EncoreProperties = mockk()
    private val service = SegmentedEncodeService(ffmpegExecutor, mediaAnalyzerService, profileService, encoreProperties)

    private fun createJobWithSegmentedEncoding(
        numSegments: Int,
        audioEncodingMode: AudioEncodingMode,
        outputFolder: String = "/output/path",
        baseName: String = "test",
        audioSegmentPadding: Double = 0.0,
        audioSegmentLength: Double = 0.0,
        numAudioSegments: Int = when (audioEncodingMode) {
            AudioEncodingMode.ENCODE_WITH_VIDEO -> 0
            AudioEncodingMode.ENCODE_SEPARATELY_FULL -> 1
            AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED -> numSegments
        },
    ) = defaultEncoreJob().copy(
        outputFolder = outputFolder,
        baseName = baseName,
        segmentedEncodingInfo = SegmentedEncodingInfo(
            segmentLength = 10.0,
            numSegments = numSegments,
            numTasks = numSegments + numAudioSegments,
            audioEncodingMode = audioEncodingMode,
            audioSegmentPadding = audioSegmentPadding,
            audioSegmentLength = audioSegmentLength,
            numAudioSegments = numAudioSegments,
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
        expectedAudioSegments: List<File>? = null,
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
        if (expectedAudioSegments != null) {
            assertEquals(expectedAudioSegments.size, operation.audioSegmentFiles?.size)
            expectedAudioSegments.forEachIndexed { index, expectedFile ->
                assertEquals(expectedFile, operation.audioSegmentFiles?.get(index))
            }
        } else {
            assertNull(operation.audioSegmentFiles)
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

        @ParameterizedTest(name = "numSegments={0}, audioMode={1} creates {2} tasks")
        @CsvSource(
            "3, ENCODE_WITH_VIDEO, 3",
            "10, ENCODE_WITH_VIDEO, 10",
            "3, ENCODE_SEPARATELY_FULL, 4",
            "1, ENCODE_SEPARATELY_FULL, 2",
            "3, ENCODE_SEPARATELY_SEGMENTED, 6",
            "5, ENCODE_SEPARATELY_SEGMENTED, 10",
        )
        fun `creates correct number of tasks`(numSegments: Int, audioEncodingMode: AudioEncodingMode, expectedTasks: Int) {
            val encoreJob = createJobWithSegmentedEncoding(numSegments, audioEncodingMode)

            val tasks = service.createTasks(encoreJob)

            assertEquals(expectedTasks, tasks.size)
        }

        @Test
        fun `creates audio-video segment tasks when using ENCODE_WITH_VIDEO mode`() {
            val encoreJob = createJobWithSegmentedEncoding(numSegments = 3, audioEncodingMode = AudioEncodingMode.ENCODE_WITH_VIDEO)

            val tasks = service.createTasks(encoreJob)

            tasks.forEachIndexed { index, task ->
                assertEquals(TaskType.AUDIOVIDEOSEGMENT, task.type)
                assertEquals(index, task.taskNo)
                assertEquals(index, task.segment)
            }
        }

        @Test
        fun `creates separate full audio task and video segments when using ENCODE_SEPARATELY_FULL mode`() {
            val encoreJob = createJobWithSegmentedEncoding(numSegments = 3, audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_FULL)

            val tasks = service.createTasks(encoreJob)

            assertEquals(TaskType.AUDIOFULL, tasks[0].type)
            assertEquals(0, tasks[0].taskNo)

            tasks.drop(1).forEachIndexed { index, task ->
                assertEquals(TaskType.VIDEOSEGMENT, task.type)
                assertEquals(index + 1, task.taskNo)
                assertEquals(index, task.segment)
            }
        }

        @Test
        fun `creates audio segments and video segments when using ENCODE_SEPARATELY_SEGMENTED mode`() {
            val encoreJob = createJobWithSegmentedEncoding(numSegments = 3, audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val tasks = service.createTasks(encoreJob)

            assertEquals(6, tasks.size)

            // First 3 tasks should be audio segments
            tasks.take(3).forEachIndexed { index, task ->
                assertEquals(TaskType.AUDIOSEGMENT, task.type)
                assertEquals(index, task.taskNo)
                assertEquals(index, task.segment)
            }

            // Next 3 tasks should be video segments
            tasks.drop(3).forEachIndexed { index, task ->
                assertEquals(TaskType.VIDEOSEGMENT, task.type)
                assertEquals(index + 3, task.taskNo)
                assertEquals(index, task.segment)
            }
        }
    }

    @Nested
    inner class PrepareJoinSegmentTest {

        @TempDir
        lateinit var tempDir: File

        @Test
        fun `prepares join operations for ENCODE_WITH_VIDEO mode`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            createSegmentFiles(workDir, "test", listOf("_720p.mp4", "_1080p.mp4"), 3)

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 3,
                audioEncodingMode = AudioEncodingMode.ENCODE_WITH_VIDEO,
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
        fun `prepares join operations for ENCODE_SEPARATELY_FULL mode`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            createSegmentFiles(workDir, "test", listOf("_720p.mp4"), 2)
            File(audioDir, "test_720p.mp4").writeText("audio")
            File(audioDir, "test_audio.mp4").writeText("audio_only")

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 2,
                audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_FULL,
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
        fun `prepares join operations for ENCODE_SEPARATELY_SEGMENTED mode`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            // Create video segments
            createSegmentFiles(workDir, "test", listOf("_720p.mp4"), 2)
            // Create audio segments
            createSegmentFiles(audioDir, "test", listOf("_720p.mp4"), 2)
            // Create audio-only segments
            createSegmentFiles(audioDir, "test", listOf("_audio.mp4"), 2)

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 2,
                audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED,
                outputFolder = outputFolder.absolutePath,
            )

            val operations = service.prepareJoinSegment(encoreJob, workDir)

            // Should have 2 operations:
            // 1. Video join operation for test_720p.mp4 (with audioSegmentFiles)
            // 2. Audio-only join operation for test_audio.mp4
            assertEquals(2, operations.size)

            // Video operation should have audio segment files
            assertOperationMatches(
                operations["test_720p.mp4"]!!,
                expectedTarget = File(outputFolder, "test_720p.mp4"),
                expectedSegments = expectedSegmentFiles(workDir, "test", "_720p.mp4", 2),
                expectedAudioSegments = expectedSegmentFiles(audioDir, "test", "_720p.mp4", 2),
            )

            // Audio-only output
            assertOperationMatches(
                operations["test_audio.mp4"]!!,
                expectedTarget = File(outputFolder, "test_audio.mp4"),
                expectedSegments = emptyList(),
                expectedAudioSegments = expectedSegmentFiles(audioDir, "test", "_audio.mp4", 2),
            )
        }

        @Test
        fun `groups segment files by suffix correctly`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            createSegmentFiles(workDir, "test", listOf(".mp4", "_high.mp4", "_low.mp4"), 2)

            val encoreJob = createJobWithSegmentedEncoding(
                numSegments = 2,
                audioEncodingMode = AudioEncodingMode.ENCODE_WITH_VIDEO,
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
            every { ffmpegExecutor.joinSegments(any(), capture(segmentListFileSlot), any(), any(), any()) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, segments, audioFile)
            val encoreJob = defaultEncoreJob().copy(outputFolder = outputFolder.absolutePath, baseName = "test")

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, audioFile, null) }
            assertSegmentListFileMatches(segmentListFileSlot.captured, File(workDir, "test_720p_filelist.txt"), segments)
        }

        @Test
        fun `joins segments without audio file`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)

            val segments = createSegmentFilesList(workDir, "test", "_720p.mp4", 2)
            val targetFile = File(outputFolder, "test_720p.mp4")

            val segmentListFileSlot = slot<File>()
            val mockMediaFile = mockk<MediaFile>()
            every { ffmpegExecutor.joinSegments(any(), capture(segmentListFileSlot), any(), null, any()) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, segments, null)
            val encoreJob = defaultEncoreJob().copy(outputFolder = outputFolder.absolutePath, baseName = "test")

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, null, null) }
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

        @Test
        fun `joins audio segments with padding`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            val audioSegments = createSegmentFilesList(audioDir, "test", "_audio.mp4", 3)
            val targetFile = File(outputFolder, "test_audio.mp4")

            val mockMediaFile = mockk<MediaFile>()
            val capturedSegmentList = slot<File>()
            every { ffmpegExecutor.joinSegments(any(), capture(capturedSegmentList), any(), null, null) } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, emptyList(), null, audioSegments)
            val encoreJob = defaultEncoreJob().copy(
                outputFolder = outputFolder.absolutePath,
                baseName = "test",
                segmentedEncodingInfo = SegmentedEncodingInfo(
                    segmentLength = 10.0,
                    numSegments = 3,
                    numTasks = 3,
                    audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED,
                    audioSegmentPadding = 2 * 8.0 / 375.0, // 2 audio frames for 48khz
                    audioSegmentLength = 8.0,
                    numAudioSegments = 4,
                ),
            )

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, null, null) }

            // Verify the audio segment list file was created with proper padding
            val audioSegmentListFile = capturedSegmentList.captured
            assertEquals(true, audioSegmentListFile.exists())

            val expectedContent = """
                file ${audioSegments[0].absolutePath}
                inpoint 0.0
                outpoint 8.0
                file ${audioSegments[1].absolutePath}
                inpoint 0.042666666666666665
                outpoint 8.042666666666667
                file ${audioSegments[2].absolutePath}
                inpoint 0.042666666666666665
                
            """.trimIndent()

            assertEquals(expectedContent, audioSegmentListFile.readText())
        }

        @Test
        fun `joins video segments with audio segment list`() {
            val (outputFolder, workDir) = setupDirectories(tempDir)
            val audioDir = File(workDir, "audio").apply { mkdirs() }

            val videoSegments = createSegmentFilesList(workDir, "test", "_720p.mp4", 2)
            val audioSegments = createSegmentFilesList(audioDir, "test", "_720p.mp4", 2)
            val targetFile = File(outputFolder, "test_720p.mp4")

            val mockMediaFile = mockk<MediaFile>()
            val capturedAudioSegmentList = slot<File>()
            every {
                ffmpegExecutor.joinSegments(any(), any(), any(), null, capture(capturedAudioSegmentList))
            } returns mockMediaFile

            val operation = SegmentedEncodeService.JoinSegmentOperation(targetFile, videoSegments, null, audioSegments)
            val encoreJob = defaultEncoreJob().copy(
                outputFolder = outputFolder.absolutePath,
                baseName = "test",
                segmentedEncodingInfo = SegmentedEncodingInfo(
                    segmentLength = 10.0,
                    numSegments = 2,
                    numTasks = 4,
                    audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED,
                    audioSegmentPadding = 0.2,
                    audioSegmentLength = 8.0,
                    numAudioSegments = 2,
                ),
            )

            val result = service.joinSegments(encoreJob, workDir, operation)

            assertEquals(mockMediaFile, result)
            verify { ffmpegExecutor.joinSegments(encoreJob, any(), targetFile, null, any()) }

            // Verify the audio segment list file was created
            val audioSegmentListFile = capturedAudioSegmentList.captured
            assertEquals(true, audioSegmentListFile.exists())
        }
    }

    @Nested
    inner class AudioEncodingModeTest {

        private fun setupEncoreProperties(audioEncodingMode: AudioEncodingMode) {
            val segmentedEncodingProps = mockk<se.svt.oss.encore.config.SegmentedEncodingProperties> {
                every { this@mockk.audioEncodingMode } returns audioEncodingMode
            }
            val encodingProps = mockk<se.svt.oss.encore.config.EncodingProperties> {
                every { segmentedEncoding } returns segmentedEncodingProps
            }
            every { encoreProperties.encoding } returns encodingProps
        }

        @Test
        fun `returns nulls for ENCODE_WITH_VIDEO mode`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_WITH_VIDEO)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_WITH_VIDEO, config.audioEncodingMode)
            assertEquals(0.0, config.audioSegmentPadding, 0.0001)
            assertEquals(0.0, config.audioSegmentLength, 0.0001)
        }

        @Test
        fun `returns nulls for ENCODE_SEPARATELY_FULL mode`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_FULL)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_FULL, config.audioEncodingMode)
            assertEquals(0.0, config.audioSegmentPadding, 0.0001)
            assertEquals(0.0, config.audioSegmentLength, 0.0001)
        }

        @Test
        fun `downgrades to ENCODE_WITH_VIDEO when no audio encodes`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    X264Encode(width = 1920, height = 1080, twoPass = false, suffix = "_test"),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_WITH_VIDEO, config.audioEncodingMode)
            assertEquals(0.0, config.audioSegmentPadding, 0.0001)
            assertEquals(0.0, config.audioSegmentLength, 0.0001)
        }

        @Test
        fun `downgrades to ENCODE_SEPARATELY_FULL when multiple sample rates with ENCODE_SEPARATELY_SEGMENTED`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 44100),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_FULL, config.audioEncodingMode)
            assertEquals(0.0, config.audioSegmentPadding, 0.0001)
            assertEquals(0.0, config.audioSegmentLength, 0.0001)
        }

        @Test
        fun `calculates audioSegmentPadding for ENCODE_SEPARATELY_SEGMENTED with single sample rate`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED, config.audioEncodingMode)
            // audioSegmentPadding = 2 * 1024 / 48000 = 0.04266...
            assertEquals(2.0 * 1024.0 / 48000.0, config.audioSegmentPadding, 0.0001)
        }

        @Test
        fun `calculates audioSegmentLength close to 256s for ENCODE_SEPARATELY_SEGMENTED`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED, config.audioEncodingMode)
            // Should be close to 256s and a multiple of frame duration (1024 / 48000 = 0.021333...)
            val frameDuration = 1024.0 / 48000.0
            val expectedLength = kotlin.math.round(256.0 / frameDuration) * frameDuration
            assertEquals(expectedLength, config.audioSegmentLength, 0.0001)
            // Verify it's actually close to 256s
            assertEquals(256.0, config.audioSegmentLength, 0.1)
        }

        @Test
        fun `uses custom audioSegmentLength from job when specified`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val customLength = 300.0
            val job = defaultEncoreJob().copy(audioSegmentLength = customLength)
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED, config.audioEncodingMode)
            assertEquals(customLength, config.audioSegmentLength)
        }

        @Test
        fun `uses job audioEncodingMode when specified`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 48000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_WITH_VIDEO)

            val job = defaultEncoreJob().copy(audioEncodingMode = AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)
            val config = service.audioEncodingConfig(job, profile)

            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED, config.audioEncodingMode)
        }

        @Test
        fun `downgrades when multiple sample rates within same output`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.GenericVideoEncode(
                        width = null,
                        height = 720,
                        twoPass = false,
                        params = linkedMapOf(),
                        audioEncode = null,
                        audioEncodes = listOf(
                            se.svt.oss.encore.model.profile.SimpleAudioEncode(samplerate = 48000),
                            se.svt.oss.encore.model.profile.SimpleAudioEncode(samplerate = 44100),
                        ),
                        suffix = "_test",
                        format = "mp4",
                        codec = "libx264",
                    ),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            // Should downgrade to ENCODE_SEPARATELY_FULL because multiple sample rates are present
            assertEquals(AudioEncodingMode.ENCODE_SEPARATELY_FULL, config.audioEncodingMode)
            assertEquals(0.0, config.audioSegmentPadding, 0.0001)
            assertEquals(0.0, config.audioSegmentLength, 0.0001)
        }

        @Test
        fun `uses default sample rate of 48000 when no sample rates found`() {
            val profile = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.GenericVideoEncode(
                        width = null,
                        height = 720,
                        twoPass = false,
                        params = linkedMapOf(),
                        audioEncode = SimpleAudioEncode(),
                        suffix = "_test",
                        format = "mp4",
                        codec = "libx264",
                    ),
                ),
            )
            every { profileService.getProfile(any()) } returns profile
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job = defaultEncoreJob()
            val config = service.audioEncodingConfig(job, profile)

            // Should use default 48000
            assertEquals(2.0 * 1024.0 / 48000.0, config.audioSegmentPadding, 0.0001)
        }

        @Test
        fun `calculates different padding for different sample rates`() {
            // Test with 44100 Hz
            val profile44100 = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 44100),
                ),
            )
            every { profileService.getProfile(any()) } returns profile44100
            setupEncoreProperties(AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)

            val job1 = defaultEncoreJob()
            val config1 = service.audioEncodingConfig(job1, profile44100)
            assertEquals(2.0 * 1024.0 / 44100.0, config1.audioSegmentPadding, 0.0001)

            // Test with 96000 Hz
            val profile96000 = Profile(
                name = "test",
                description = "test",
                encodes = listOf(
                    se.svt.oss.encore.model.profile.AudioEncode(samplerate = 96000),
                ),
            )
            every { profileService.getProfile(any()) } returns profile96000

            val job2 = defaultEncoreJob()
            val config2 = service.audioEncodingConfig(job2, profile96000)
            assertEquals(2.0 * 1024.0 / 96000.0, config2.audioSegmentPadding, 0.0001)

            // Higher sample rate should have smaller padding
            assert(config2.audioSegmentPadding < config1.audioSegmentPadding)
        }
    }
}
