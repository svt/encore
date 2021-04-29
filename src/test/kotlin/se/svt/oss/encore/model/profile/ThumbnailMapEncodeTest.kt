// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.output.OutputAssert.assertThat
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailMapEncodeTest {

    private val encode = ThumbnailMapEncode(
        aspectWidth = 16,
        aspectHeight = 9,
        tileHeight = 90,
        cols = 12,
        rows = 20
    )

    private val videoFile = defaultVideoFile

    @Test
    fun `invalid number of frames returns null`() {
        val output = encode.getOutput(
            videoFile = videoFile.copy(videoStreams = videoFile.videoStreams.map { it.copy(numFrames = 15) }),
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )

        assertThat(output).isNull()
    }

    @Test
    fun `thumbnail time set returns null`() {
        val output = encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = 1000,
            audioMixPresets = emptyMap()
        )

        assertThat(output).isNull()
    }

    @Test
    fun `correct filter`() {
        val output = encode.getOutput(
            videoFile = videoFile,
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = emptyMap()
        )
        assertThat(output).hasOutput("/some/output/folder/test_12x20_160x90_thumbnail_map.jpg")
        assertThat(output).hasAudio(null)
        assertThat(output).hasVideo(
            VideoStreamEncode(
                params = listOf("-frames", "1", "-q:v", "5"),
                filter = "select=not(mod(n\\,1)),pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,scale=-1:90,tile=12x20",
                twoPass = false
            )
        )
    }
}
