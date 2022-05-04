// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailMapEncodeTest {

    private val encode = ThumbnailMapEncode(
        tileWidth = 160,
        tileHeight = 90,
        cols = 12,
        rows = 20
    )

    @Test
    fun `correct output`() {
        val output = encode.getOutput(defaultEncoreJob(), emptyMap())
        assertThat(output)
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasId("_12x20_160x90_thumbnail_map.jpg")
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-q:v", "5"),
                    filter = "select=not(mod(n\\,1)),pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,scale=-1:90",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
            )
    }

    @Test
    fun `correct output seekTo and duration`() {
        val output = ThumbnailMapEncode(cols = 6, rows = 10).getOutput(defaultEncoreJob().copy(seekTo = 1.0, duration = 5.0), emptyMap())
        assertThat(output)
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasId("_6x10_160x90_thumbnail_map.jpg")
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-q:v", "5"),
                    filter = "select=not(mod(n\\,2))*gte(t\\,1.0),pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,scale=-1:90",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
            )
    }

    @Test
    fun `invalid number of frames optional returns null`() {
        val defaultEncoreJob = defaultEncoreJob()
        val output = encode.getOutput(
            job = defaultEncoreJob.copy(
                thumbnailTime = 1.0,
                inputs = listOf(
                    AudioVideoInput(
                        uri = "/input/test.mp4",
                        analyzed = defaultVideoFile.copy(
                            videoStreams = defaultVideoFile.videoStreams.map {
                                it.copy(
                                    numFrames = 15
                                )
                            }
                        )
                    )
                ),
            ),
            audioMixPresets = emptyMap()
        )
        assertThat(output).isNull()
    }

    @Test
    fun `invalid number of frames not optional throws`() {
        val defaultEncoreJob = defaultEncoreJob()
        assertThatThrownBy {
            encode.copy(optional = false).getOutput(
                job = defaultEncoreJob.copy(
                    thumbnailTime = 1.0,
                    inputs = listOf(
                        AudioVideoInput(
                            uri = "/input/test.mp4",
                            analyzed = defaultVideoFile.copy(
                                videoStreams = defaultVideoFile.videoStreams.map {
                                    it.copy(
                                        numFrames = 15
                                    )
                                }
                            )
                        )
                    ),
                ),
                audioMixPresets = emptyMap()
            )
        }.hasMessageContaining("Video input main did not contain enough frames to generate thumbnail map")
    }
}
