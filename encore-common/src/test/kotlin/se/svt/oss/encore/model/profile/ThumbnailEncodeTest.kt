// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.longVideoFile
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailEncodeTest {

    private val encode = ThumbnailEncode(
        percentages = listOf(10, 50),
        thumbnailWidth = 1920,
        thumbnailHeight = 1080,
    )

    @Test
    fun `use percentages for filter`() {
        val output = encode.getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr", "-q:v", "5"),
                    filter = "select=(isnan(prev_pts)+lt(prev_pts*TB\\,1.0))*gte(pts*TB\\,1.0)+(isnan(prev_pts)+lt(prev_pts*TB\\,5.0))*gte(pts*TB\\,5.0),scale=w=1920:h=1080:out_range=jpeg",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `use thumbnail time`() {
        val output = encode.getOutput(
            job = defaultEncoreJob().copy(
                thumbnailTime = 5.0,
            ),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr", "-q:v", "5"),
                    filter = "select=(isnan(prev_pts)+lt(prev_pts*TB\\,5.0))*gte(pts*TB\\,5.0),scale=w=1920:h=1080:out_range=jpeg",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `use interval`() {
        val selectorEncode = encode.copy(
            intervalSeconds = 250.0,
            suffixZeroPad = 4,
        )
        val output = selectorEncode.getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties(),
        )

        assertThat(output)
            .hasOutput("test_thumb%04d.jpg")
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr", "-q:v", "5"),
                    filter = "select=isnan(prev_selected_t)+gt(floor(t/250.0)\\,floor(prev_selected_t/250.0)),scale=w=1920:h=1080:out_range=jpeg",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `seekTo and duration set`() {
        val output = encode.getOutput(
            job = defaultEncoreJob().copy(
                seekTo = 1.0,
                duration = 4.0,
            ),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr", "-q:v", "5"),
                    filter = "select=(isnan(prev_pts)+lt(prev_pts*TB\\,1.4))*gte(pts*TB\\,1.4)+(isnan(prev_pts)+lt(prev_pts*TB\\,3.0))*gte(pts*TB\\,3.0),scale=w=1920:h=1080:out_range=jpeg",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `inputSeekTo and duration set`() {
        val output = encode.getOutput(
            job = defaultEncoreJob().copy(
                seekTo = 10.0,
                thumbnailTime = 1351.0,
                duration = 600.0,
                inputs = listOf(
                    AudioVideoInput(
                        uri = "/input/test.mp4",
                        analyzed = longVideoFile,
                        seekTo = 1190.0,
                    ),
                ),
            ),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr", "-q:v", "5"),
                    filter = "select=(isnan(prev_pts)+lt(prev_pts*TB\\,161.0))*gte(pts*TB\\,161.0),scale=w=1920:h=1080:out_range=jpeg",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `unmapped input optional returns null`() {
        val output = encode.copy(inputLabel = "other", optional = true).getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output).isNull()
    }

    @Test
    fun `unmapped input not optional throws`() {
        assertThatThrownBy {
            encode.copy(inputLabel = "other", optional = false).getOutput(
                job = defaultEncoreJob(),
                encodingProperties = EncodingProperties(),
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No video input with label other!")
    }
}
