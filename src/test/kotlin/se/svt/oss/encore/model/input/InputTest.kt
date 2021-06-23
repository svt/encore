// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.input

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.multipleAudioFile
import se.svt.oss.encore.defaultVideoFile

internal class InputTest {

    private val extraVideoFile = defaultVideoFile.copy(file = "extra.mp4", duration = 12.0)

    private val inputs = listOf(
        VideoInput(
            uri = "/input1.mp4",
            params = linkedMapOf("a" to "b"),
            videoLabel = "extra",
            analyzed = extraVideoFile
        ),
        AudioVideoInput(
            uri = "http://input2",
            analyzed = defaultVideoFile
        ),
        AudioInput(
            uri = "/input3.mxf",
            params = linkedMapOf("c" to "d"),
            audioLabel = "other",
            analyzed = multipleAudioFile
        )
    )

    @Test
    fun testInputParamsWithDuration() {
        val params = inputs.inputParams(60.5)
        assertThat(params)
            .isEqualTo(
                listOf(
                    "-a", "b", "-t", "60.5", "-i", "/input1.mp4",
                    "-t", "60.5", "-i", "http://input2",
                    "-c", "d", "-t", "60.5", "-i", "/input3.mxf"
                )
            )
    }

    @Test
    fun testInputParamsWithOutDuration() {
        val params = inputs.inputParams(null)
        assertThat(params)
            .isEqualTo(
                listOf(
                    "-a", "b", "-i", "/input1.mp4",
                    "-i", "http://input2",
                    "-c", "d", "-i", "/input3.mxf"
                )
            )
    }

    @Test
    fun testMaxDuration() {
        val maxDuration = inputs.maxDuration()
        assertThat(maxDuration).isEqualTo(12.0)
    }

    @Test
    fun testAnalyzedAudio() {
        val analyzedAudio = inputs.analyzedAudio("other")
        assertThat(analyzedAudio).isSameAs(multipleAudioFile)
    }

    @Test
    fun testAnalyzedAudioDuplicates() {
        assertThatThrownBy { (inputs + inputs.last()).analyzedAudio("other") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Inputs contains duplicate audio labels!")
    }

    @Test
    fun testAnalyzedVideo() {
        val analyzedVideo = inputs.analyzedVideo(DEFAULT_VIDEO_LABEL)
        assertThat(analyzedVideo).isSameAs(defaultVideoFile)
    }

    @Test
    fun testAnalyzedVideoDuplicates() {
        assertThatThrownBy { (inputs + inputs.first()).analyzedVideo("extra") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Inputs contains duplicate video labels!")
    }
}
