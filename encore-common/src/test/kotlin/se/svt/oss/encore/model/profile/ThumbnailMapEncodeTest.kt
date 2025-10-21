// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailMapEncodeTest {

    private val encode = ThumbnailMapEncode(
        tileWidth = 160,
        tileHeight = 90,
        cols = 12,
        rows = 20,
    )

    @Test
    fun `correct output`() {
        val output = encode.getOutput(defaultEncoreJob(), EncodingProperties())
        assertThat(output)
            .hasNoAudioStreams()
            .hasId("_12x20_160x90_thumbnail_map.jpg")
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr"),
                    filter = "select=isnan(prev_selected_t)+gt(floor(t/0.041666666666666664)\\,floor(prev_selected_t/0.041666666666666664)),pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,scale=-1:90",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL),
                ),
            )
    }

    @Test
    fun `correct output seekTo and duration`() {
        val output = ThumbnailMapEncode(cols = 6, rows = 10)
            .getOutput(
                defaultEncoreJob()
                    .copy(seekTo = 1.0, duration = 5.0),
                EncodingProperties(),
            )
        assertThat(output)
            .hasNoAudioStreams()
            .hasId("_6x10_160x90_thumbnail_map.jpg")
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-fps_mode", "vfr"),
                    filter = "select=gte(t\\,1.0)*(isnan(prev_selected_t)+gt(floor((t-1.0)/0.08333333333333333)\\,floor((prev_selected_t-1.0)/0.08333333333333333))),pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,scale=-1:90",
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
            .hasMessageContaining("No input with label other!")
    }

    @Test
    fun `returns null when not enabled`() {
        val output = encode.copy(enabled = false).getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties(),
        )
        assertThat(output).isNull()
    }
}
