// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.longVideoFile
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.output.VideoStreamEncode

class ThumbnailEncodeTest {

    private val encode = ThumbnailEncode(
        percentages = listOf(10, 50),
        thumbnailWidth = 1920,
        thumbnailHeight = 1080
    )

    @Test
    fun `use percentages for filter`() {
        val output = encode.getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties()
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-frames:v", "2", "-vsync", "vfr", "-q:v", "5"),
                    filter = "select=eq(n\\,25)+eq(n\\,125),scale=1920:1080",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
            )
    }

    @Test
    fun `use thumbnail time`() {
        val output = encode.getOutput(
            job = defaultEncoreJob().copy(
                thumbnailTime = 5.0,
            ),
            encodingProperties = EncodingProperties()
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-frames:v", "1", "-vsync", "vfr", "-q:v", "5"),
                    filter = "select=eq(n\\,125),scale=1920:1080",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
            )
    }

    @Test
    fun `thumbnail time invalid framerate not optional throws`() {
        val defaultEncoreJob = defaultEncoreJob()
        assertThatThrownBy {
            encode.getOutput(
                job = defaultEncoreJob.copy(
                    thumbnailTime = 1.0,
                    inputs = listOf(
                        AudioVideoInput(
                            uri = "/input/test.mp4",
                            analyzed = defaultVideoFile.copy(
                                videoStreams = defaultVideoFile.videoStreams.map {
                                    it.copy(
                                        frameRate = "0/0"
                                    )
                                }
                            )
                        )
                    ),
                ),
                encodingProperties = EncodingProperties()
            )
        }.hasMessageContaining("No framerate detected")
    }

    @Test
    fun `thumbnail time invalid framerate optional returns null`() {
        val defaultEncoreJob = defaultEncoreJob()
        val output = encode.copy(optional = true).getOutput(
            job = defaultEncoreJob.copy(
                thumbnailTime = 1.0,
                inputs = listOf(
                    AudioVideoInput(
                        uri = "/input/test.mp4",
                        analyzed = defaultVideoFile.copy(
                            videoStreams = defaultVideoFile.videoStreams.map {
                                it.copy(
                                    frameRate = "0/0"
                                )
                            }
                        )
                    )
                ),
            ),
            encodingProperties = EncodingProperties()
        )
        assertThat(output).isNull()
    }

    @Test
    fun `seekTo and duration set`() {
        val output = encode.getOutput(
            job = defaultEncoreJob().copy(
                seekTo = 1.0,
                duration = 4.0
            ),
            encodingProperties = EncodingProperties()
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-frames:v", "2", "-vsync", "vfr", "-q:v", "5"),
                    filter = "select=eq(n\\,35)+eq(n\\,75),scale=1920:1080",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
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
                        seekTo = 1190.0
                    )
                )
            ),
            encodingProperties = EncodingProperties()
        )
        assertThat(output)
            .hasOutput("test_thumb%02d.jpg")
            .hasSeekable(false)
            .hasNoAudioStreams()
            .hasVideo(
                VideoStreamEncode(
                    params = listOf("-frames:v", "1", "-vsync", "vfr", "-q:v", "5"),
                    filter = "select=eq(n\\,4025),scale=1920:1080",
                    twoPass = false,
                    inputLabels = listOf(DEFAULT_VIDEO_LABEL)
                )
            )
    }

    @Test
    fun `unmapped input optional returns null`() {
        val output = encode.copy(inputLabel = "other", optional = true).getOutput(
            job = defaultEncoreJob(),
            encodingProperties = EncodingProperties()
        )
        assertThat(output).isNull()
    }

    @Test
    fun `unmapped input not optional throws`() {
        assertThatThrownBy {
            encode.copy(inputLabel = "other", optional = false).getOutput(
                job = defaultEncoreJob(),
                encodingProperties = EncodingProperties()
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No video input with label other!")
    }
}
