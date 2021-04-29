// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.output.OutputAssert.assertThat
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailEncodeTest {

    private val encode = ThumbnailEncode(
        percentages = listOf(50),
        thumbnailWidth = 1920,
        thumbnailHeight = 1080
    )

    private val videoFile = defaultVideoFile

    @Test
    fun `use percentages for filter`() {
        val output = encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )
        assertThat(output).hasOutput("/some/output/folder/test_thumb%02d.jpg")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-vsync", "vfr", "-q:v", "5"),
                filter = "select=eq(n\\,125),scale=1920:1080",
                twoPass = false
            )
        )
    }

    @Test
    fun `use thumbnail time`() {
        val output = encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = 5000,
            audioMixPresets = emptyMap()
        )
        assertThat(output).hasOutput("/some/output/folder/test_thumb%02d.jpg")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-vsync", "vfr", "-q:v", "5"),
                filter = "select=eq(n\\,125),scale=1920:1080",
                twoPass = false
            )
        )
    }
}
