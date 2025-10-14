// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.awaitility.Durations
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.input.AudioInput
import se.svt.oss.encore.model.input.VideoInput
import se.svt.oss.encore.model.profile.ChannelLayout
import se.svt.oss.mediaanalyzer.file.ImageFile
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.io.File
import java.time.Duration

@ActiveProfiles("test")
@WireMockTest
class EncoreIntegrationTest(wireMockRuntimeInfo: WireMockRuntimeInfo) : EncoreIntegrationTestBase(wireMockRuntimeInfo) {

    @Test
    fun jobIsSuccessfulSurround(@TempDir outputDir: File) {
        val createdJob = successfulTest(
            job(outputDir = outputDir, file = testFileSurround),
            defaultExpectedOutputFiles(outputDir, testFileSurround) +
                listOf(
                    expectedFile(outputDir, testFileSurround, "STEREO_DE.mp4"),
                    expectedFile(outputDir, testFileSurround, "SURROUND.mp4"),
                    expectedFile(outputDir, testFileSurround, "SURROUND_DE.mp4"),
                ),
        )
    }

    @Test
    fun jobIsSuccessfulSurroundSegmentedEncode(@TempDir outputDir: File) {
        val job = job(outputDir = outputDir, file = testFileSurround).copy(
            profile = "separate-video-audio",
            segmentLength = 3.84,
            priority = 100,
        )
        val expectedFiles = listOf(
            "x264_3100.mp4",
            "STEREO.mp4",
        )
            .map { expectedFile(outputDir, testFileSurround, it) } +
            listOf(
                expectedFile(outputDir, testFileSurround, "STEREO_DE.mp4"),
                expectedFile(outputDir, testFileSurround, "SURROUND.mp4"),
                expectedFile(outputDir, testFileSurround, "SURROUND_DE.mp4"),
            )

        val createdJob = successfulTest(
            job,
            expectedFiles,
        )
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_WITH_VIDEO)
            .hasNumSegments(3)
            .hasNumAudioSegments(0)
            .hasNumTasks(3)
    }

    @Test
    fun jobIsSuccessfulSurroundSegmentedEncodeSeparateAudio(@TempDir outputDir: File) {
        val job = job(outputDir = outputDir, file = testFileSurround).copy(
            profile = "separate-video-audio",
            segmentLength = 3.84,
            audioEncodingMode = se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_FULL,
            priority = 100,
        )
        val expectedFiles = listOf(
            "x264_3100.mp4",
            "STEREO.mp4",
        )
            .map { expectedFile(outputDir, testFileSurround, it) } +
            listOf(
                expectedFile(outputDir, testFileSurround, "STEREO_DE.mp4"),
                expectedFile(outputDir, testFileSurround, "SURROUND.mp4"),
                expectedFile(outputDir, testFileSurround, "SURROUND_DE.mp4"),
            )

        val createdJob = successfulTest(
            job,
            expectedFiles,
        )
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_FULL)
            .hasNumSegments(3)
            .hasNumTasks(4)
    }

    @Test
    fun jobIsSuccessfulSegmentedEncodeSeparatelySegmentedAudio(@TempDir outputDir: File) {
        val job = job(outputDir = outputDir, file = testFileSurround).copy(
            profile = "separate-video-audio",
            segmentLength = 3.84,
            audioEncodingMode = se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED,
            audioSegmentLength = 8.0,
            priority = 100,
            profileParams = mapOf("enableSurround" to "false"), // segmented audio encode not supported for surround
        )
        val expectedFiles = listOf(
            "x264_3100.mp4",
            "STEREO.mp4",
        )
            .map { expectedFile(outputDir, testFileSurround, it) } +
            listOf(
                expectedFile(outputDir, testFileSurround, "STEREO_DE.mp4"),
            )

        val createdJob = successfulTest(
            job,
            expectedFiles,
        )
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)
            .hasNumSegments(3)
            .hasNumTasks(5) // 2 audio segments + 3 video segments
    }

    @Test
    fun multipleAudioStreamsOutputSegmentedEncode(@TempDir outputDir: File) {
        val baseName = "multiple_audio"
        val job = job(outputDir).copy(
            baseName = baseName,
            profile = "audio-streams",
            segmentLength = 3.84,
            priority = 100,
        )
        val expectedOutPut = listOf(outputDir.resolve("$baseName.mp4").absolutePath)
        val createdJob = successfulTest(job, expectedOutPut)
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_WITH_VIDEO)
            .hasNumSegments(3)
            .hasNumTasks(3)
        assertThat(createdJob.output)
            .hasSize(1)
        assertThat(createdJob.output[0])
            .isInstanceOf(VideoFile::class.java)
        val audioStreams = (createdJob.output[0] as VideoFile).audioStreams
        assertThat(audioStreams).hasSize(2)
        assertThat(audioStreams[0])
            .hasFormat("AC-3")
            .hasCodec("ac3")
            .hasDurationCloseTo(10.0, 0.1)
            .hasChannels(6)
            .hasSamplingRate(48000)
        assertThat(audioStreams[1])
            .hasFormat("AAC")
            .hasCodec("aac")
            .hasDurationCloseTo(10.0, 0.1)
            .hasChannels(2)
            .hasSamplingRate(48000)
    }

    @Test
    fun multipleAudioStreamsOutputSegmentedEncodeSeparateAudio(@TempDir outputDir: File) {
        val baseName = "multiple_audio"
        val job = job(outputDir).copy(
            baseName = baseName,
            profile = "audio-streams",
            segmentLength = 3.84,
            priority = 100,
            audioEncodingMode = se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_FULL,
        )
        val expectedOutPut = listOf(outputDir.resolve("$baseName.mp4").absolutePath)
        val createdJob = successfulTest(job, expectedOutPut)

        assertThat(createdJob.output)
            .hasSize(1)
        assertThat(createdJob.output[0])
            .isInstanceOf(VideoFile::class.java)
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_FULL)
            .hasNumSegments(3)
            .hasNumTasks(4)

        val audioStreams = (createdJob.output[0] as VideoFile).audioStreams
        assertThat(audioStreams).hasSize(2)
        assertThat(audioStreams[0])
            .hasFormat("AC-3")
            .hasCodec("ac3")
            .hasDurationCloseTo(10.0, 0.1)
            .hasChannels(6)
            .hasSamplingRate(48000)
        assertThat(audioStreams[1])
            .hasFormat("AAC")
            .hasCodec("aac")
            .hasDurationCloseTo(10.0, 0.1)
            .hasChannels(2)
            .hasSamplingRate(48000)
    }

    @Test
    fun multipleInputsWithSeekAndDuration(@TempDir outputDir: File) {
        val baseName = "clip"
        val job = job(outputDir).copy(
            baseName = baseName,
            profile = "multiple-inputs",
            seekTo = 1.0,
            duration = 6.0,
            thumbnailTime = 4.0,
            inputs = listOf(
                VideoInput(
                    uri = testFileMultipleVideo.file.absolutePath,
                    videoStream = 1,
                    probeInterlaced = false,
                    seekTo = 1.0,
                ),
                AudioInput(
                    uri = testFileMultipleAudio.file.absolutePath,
                    audioStream = 1,
                    seekTo = 1.0,
                ),
                AudioInput(
                    uri = testFileSurround.file.absolutePath,
                    channelLayout = ChannelLayout.CH_LAYOUT_5POINT1,
                    audioLabel = "alt",
                    seekTo = 1.0,
                ),
            ),
        )

        val expectedOutputFiles = listOf(
            "x264_3100.mp4",
            "STEREO.mp4",
            "STEREO_DE.mp4",
            "STEREO_ALT.mp4",
            "SURROUND.mp4",
            "thumb01.jpg",
            "6x10_160x90_thumbnail_map.jpg",
        ).map { expectedFile(outputDir, baseName, it) }

        val createdJob = successfulTest(job, expectedOutputFiles)

        val videoOutput = createdJob.output.first { it.file.endsWith("_x264_3100.mp4") }
        assertThat(videoOutput).isInstanceOf(VideoFile::class.java)
        videoOutput as VideoFile
        assertThat(videoOutput.highestBitrateVideoStream)
            .hasWidth(1920)
            .hasHeight(1080)
        assertThat(createdJob.output.filterIsInstance<MediaContainer>())
            .hasSize(5)
            .allSatisfy {
                assertThat(it).hasDurationCloseTo(6.0, 0.1)
            }
        assertThat(createdJob.output.first { it.file.endsWith("6x10_160x90_thumbnail_map.jpg") } as? ImageFile)
            .hasWidth(6 * 160)
            .hasHeight(10 * 90)
    }

    @Test
    fun jobIsSuccessfulStereo(@TempDir outputDir: File) {
        successfulTest(
            job(outputDir = outputDir, file = testFileStereo),
            defaultExpectedOutputFiles(outputDir, testFileStereo) +
                listOf(expectedFile(outputDir, testFileStereo, "STEREO_DE.mp4")),
        )
    }

    @Test
    fun jobWithInputParamsForRawVideo(@TempDir outputDir: File) {
        val inputFile = ClassPathResource("input/testyuv.yuv")
        val encoreJob = job(outputDir = outputDir, file = inputFile)

        successfulTest(
            encoreJob.copy(
                profile = "archive",
                profileParams = linkedMapOf("suffix" to "_raw", "height" to 1080),
                inputs = listOf(
                    VideoInput(
                        uri = inputFile.file.absolutePath,
                        params = linkedMapOf(
                            "f" to "rawvideo",
                            "video_size" to "640x360",
                            "framerate" to "25",
                            "pixel_format" to "yuv420p",
                        ),
                    ),
                ),
            ),

            listOf(outputDir.resolve("testyuv_raw.mxf").absolutePath),
        )
    }

    @Test
    fun highPriorityJobIsRunInParallel(@TempDir outputDir1: File, @TempDir outputDir2: File) {
        val standardPriorityJob = createAndAwaitJob(
            job = job(
                outputDir = outputDir1,
                priority = 0,
            ),
        ) { it.status == Status.IN_PROGRESS }

        val highPriorityJob = createAndAwaitJob(
            job = job(
                outputDir = outputDir2,
                priority = 100,
            ),
        ) { it.status == Status.IN_PROGRESS }

        encoreClient.cancel(standardPriorityJob.id)
        awaitJob(standardPriorityJob.id) { it.status.isCompleted }

        encoreClient.cancel(highPriorityJob.id)
        awaitJob(highPriorityJob.id) { it.status.isCompleted }
    }

    @Test
    fun jobIsCancelled(@TempDir outputDir: File) {
        var createdJob = createAndAwaitJob(
            job(outputDir),
            pollInterval = Duration.ofMillis(200),
        ) {
            it.status == Status.IN_PROGRESS
        }

        encoreClient.cancel(createdJob.id)

        createdJob = awaitJob(
            jobId = createdJob.id,
            pollInterval = Durations.ONE_SECOND,
            timeout = Durations.ONE_MINUTE,
        ) { it.status.isCompleted }

        assertThat(createdJob)
            .hasStatus(Status.CANCELLED)
    }

    @Test
    fun jobIsFailedOnIncompatible(@TempDir outputDir: File) {
        val createdJob = createAndAwaitJob(
            job = job(outputDir).copy(profile = "dpb_size_failed"),
            pollInterval = Durations.ONE_SECOND,
            timeout = Durations.ONE_MINUTE,
        ) { it.status.isCompleted }

        assertThat(createdJob)
            .hasStatus(Status.FAILED)

        assertThat(createdJob.message)
            .contains("Coding might not be compatible on all devices")
    }

    @Test
    fun jobIsSuccessfulAudioOnlySegmentedEncode(@TempDir outputDir: File) {
        val job = job(outputDir = outputDir, file = testFileSurround).copy(
            profile = "audio-only",
            segmentLength = 3.84,
            audioEncodingMode = se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED,
            audioSegmentLength = 235 * 1024 / 48000.0, // 235 audio frames ~= 5.013333s
            priority = 100,
        )
        val expectedFiles = listOf(
            "STEREO.mp4",
            "STEREO_DE.mp4",
        ).map { expectedFile(outputDir, testFileSurround, it) }

        val createdJob = successfulTest(
            job,
            expectedFiles,
        )
        assertThat(createdJob.segmentedEncodingInfo)
            .hasAudioEncodingMode(se.svt.oss.encore.model.AudioEncodingMode.ENCODE_SEPARATELY_SEGMENTED)
            .hasNumSegments(0) // No video segments
            .hasNumTasks(2) // Only 2 audio segments
    }
}
