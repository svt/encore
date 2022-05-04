// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.mediaanalyzer.file.AudioStream

class AudioEncodeTest {

    private val audioEncode = AudioEncode(
        codec = "aac",
        bitrate = null,
        samplerate = 48000,
        channels = 2
    )

    private val videoFile = defaultVideoFile

    @Test
    fun `no audio streams throws exception`() {
        assertThatThrownBy {
            audioEncode.getOutput(job(), mapOf("default" to AudioMixPreset(fallbackToAuto = false)))
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No audio mix preset for 'default': 0 -> 2 channels")
    }

    @Test
    fun `not supported soundtype throws exception`() {
        val job = job(getAudioStream(1), getAudioStream(2))
        assertThatThrownBy {
            audioEncode.getOutput(job, mapOf("default" to AudioMixPreset(fallbackToAuto = false)))
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Audio layout of audio input 'main' is not supported!")
    }

    @Test
    fun `valid output`() {
        val output = audioEncode.getOutput(job(getAudioStream(6)), mapOf("default" to AudioMixPreset()))
        assertThat(output)
            .hasOutput("test_aac_2ch.mp4")
            .hasSeekable(true)
            .hasVideo(null)
            .hasId("_aac_2ch.mp4")
            .hasOnlyAudioStreams(
                AudioStreamEncode(
                    params = listOf("-ac:a:{stream_index}", "2", "-c:a:{stream_index}", "aac", "-ar:a:{stream_index}", "48000"),
                    inputLabels = listOf(DEFAULT_AUDIO_LABEL)
                )
            )
    }

    @Test
    fun `valid output all values set`() {
        val audioEncodeLocal = AudioEncode(
            codec = "aac",
            bitrate = "72000",
            samplerate = 48000,
            channels = 2,
            params = linkedMapOf("profile:a" to "LC", "cutoff" to "14000"),
            filters = listOf("1", "3")
        )

        val output = audioEncodeLocal.getOutput(
            job = job(getAudioStream(6)),
            audioMixPresets = mapOf("default" to AudioMixPreset(panMapping = mapOf(6 to mapOf(2 to "stereo|c0=c0|c1=c1"))))
        )
        assertThat(output)
            .hasOutput("test_aac_2ch.mp4")
            .hasVideo(null)
            .hasOnlyAudioStreams(
                AudioStreamEncode(
                    listOf("-c:a:{stream_index}", "aac", "-ar:a:{stream_index}", "48000", "-b:a:{stream_index}", "72000", "-profile:a", "LC", "-cutoff", "14000"),
                    "pan=stereo|c0=c0|c1=c1,1,3",
                    listOf(
                        DEFAULT_AUDIO_LABEL
                    )
                )
            )
    }

    @Test
    fun `valid output with no matching audio preset optional gives null`() {
        val audioEncodeLocal = audioEncode.copy(
            audioMixPreset = "de",
            optional = true
        )

        val output = audioEncodeLocal.getOutput(
            job = job(getAudioStream(6)),
            audioMixPresets = mapOf("de" to AudioMixPreset(fallbackToAuto = false))
        )
        assertThat(output).isNull()
    }

    @Test
    fun `valid output with no matching audio preset not optional throws`() {
        val audioEncodeLocal = audioEncode.copy(
            audioMixPreset = "de",
        )

        assertThatThrownBy {
            audioEncodeLocal.getOutput(job(getAudioStream(6)), mapOf("de" to AudioMixPreset(fallbackToAuto = false)))
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No audio mix preset for 'de': 6 -> 2 channels")
    }

    @Test
    fun `unmapped input optional returns null`() {
        val audioEncodeLocal = audioEncode.copy(inputLabel = "other", optional = true)
        val output = audioEncodeLocal.getOutput(job(getAudioStream(6)), mapOf("default" to AudioMixPreset()))
        assertThat(output).isNull()
    }

    @Test
    fun `unmapped input not optional throws`() {
        val audioEncodeLocal = audioEncode.copy(inputLabel = "other")
        assertThatThrownBy {
            audioEncodeLocal.getOutput(job(getAudioStream(6)), mapOf("de" to AudioMixPreset(fallbackToAuto = false)))
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Can not generate test_aac_2ch.mp4! No audio input with label 'other'.")
    }

    private fun job(vararg audioStreams: AudioStream) =
        defaultEncoreJob().copy(
            inputs = listOf(
                AudioVideoInput(
                    uri = defaultVideoFile.file,
                    analyzed = videoFile.copy(audioStreams = audioStreams.toList())
                )
            )
        )

    private fun getAudioStream(channelCount: Int) = AudioStream(
        format = "format",
        codec = "aac",
        duration = 10.0,
        channels = channelCount,
        samplingRate = 23123,
        bitrate = 213123
    )
}
