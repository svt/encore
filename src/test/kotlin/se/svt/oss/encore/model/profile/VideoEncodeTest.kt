// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.OutputAssert.assertThat
import se.svt.oss.encore.model.output.VideoStreamEncode

class VideoEncodeTest {

    private val x264Encode = X264Encode(
        width = null,
        height = 1080,
        twoPass = false,
        ffmpegParams = LinkedHashMap(),
        codecParams = LinkedHashMap(),
        filters = emptyList(),
        audioEncode = null,
        suffix = "suffix",
        format = "mp4"
    )

    private val videoFile = defaultVideoFile

    @Test
    fun `X265 with Audio`() {
        val x265Encode = X265Encode(
            width = 1920,
            height = 1080,
            twoPass = true,
            ffmpegParams = LinkedHashMap(),
            codecParams = LinkedHashMap(),
            filters = emptyList(),
            audioEncode = AudioEncode(),
            suffix = "suffix",
            format = "mp4"
        )

        val output = x265Encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = mapOf("default" to AudioMixPreset())
        )

        assertThat(output).hasOutput("/some/output/folder/test_x265_suffix.mp4")
        assertThat(output).hasAudio(AudioStreamEncode(listOf("-ac", "2", "-c:a", "libfdk_aac", "-ar", "48000"), null))
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-x265-params", "", "-c:v", "libx265"),
                filter = "scale=1920:1080:force_original_aspect_ratio=decrease:force_divisible_by=2",
                twoPass = true
            )
        )
    }

    @Test
    fun `without debugOverlay`() {
        val output = x264Encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )

        assertThat(output).hasOutput("/some/output/folder/test_x264_suffix.mp4")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-x264-params", "", "-c:v", "libx264"),
                filter = "scale=-2:1080",
                twoPass = false
            )
        )
    }

    @Test
    fun `with debugOverlay`() {
        val output = x264Encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = true,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )

        assertThat(output).hasOutput("/some/output/folder/test_x264_suffix.mp4")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-x264-params", "", "-c:v", "libx264"),
                filter = "scale=-2:1080,drawtext=text=suffix-x264:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10",
                twoPass = false
            )
        )
    }

    @Test
    fun `without height and width`() {
        val output = x264Encode.copy(height = null, width = null).getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = true,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )

        assertThat(output).hasOutput("/some/output/folder/test_x264_suffix.mp4")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-x264-params", "", "-c:v", "libx264"),
                filter = "drawtext=text=suffix-x264:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10",
                twoPass = false
            )
        )
    }
}
