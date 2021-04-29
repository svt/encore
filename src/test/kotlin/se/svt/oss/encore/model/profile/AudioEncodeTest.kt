// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.OutputAssert.assertThat
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
        assertThrows<RuntimeException> {
            audioEncode.getOutput(
                videoFile = videoFile.copy(audioStreams = listOf()),
                outputFolder = "/some/output/folder",
                debugOverlay = false,
                thumbnailTime = null,
                audioMixPresets = mapOf("default" to AudioMixPreset(fallbackToAuto = false))
            )
        }
    }

    @Test
    fun `not supported soundtype throws exception`() {
        assertThrows<RuntimeException> {
            audioEncode.getOutput(
                videoFile = videoFile.copy(audioStreams = listOf(getAudioStream(1), getAudioStream(2))),
                outputFolder = "/some/output/folder",
                debugOverlay = false,
                thumbnailTime = null,
                audioMixPresets = emptyMap()
            )
        }
    }

    @Test
    fun `valid output`() {
        val output = audioEncode.getOutput(
            videoFile = videoFile.copy(audioStreams = listOf(getAudioStream(6))),
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = mapOf("default" to AudioMixPreset())
        )
        assertThat(output).hasOutput("/some/output/folder/test_aac_2ch.mp4")
        assertThat(output).hasVideo(null)
        assertThat(output).hasAudio(AudioStreamEncode(listOf("-ac", "2", "-c:a", "aac", "-ar", "48000"), null))
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
            videoFile = videoFile.copy(audioStreams = listOf(getAudioStream(6))),
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = mapOf("default" to AudioMixPreset(panMapping = mapOf(6 to mapOf(2 to "stereo|c0=c0|c1=c1"))))
        )
        assertThat(output)
            .hasOutput("/some/output/folder/test_aac_2ch.mp4")
            .hasVideo(null)
            .hasAudio(AudioStreamEncode(listOf("-c:a", "aac", "-ar", "48000", "-b:a", "72000", "-profile:a", "LC", "-cutoff", "14000"), "pan=stereo|c0=c0|c1=c1,1,3"))
    }

    @Test
    fun `valid output with no matching audio preset and skip set gives null`() {
        val audioEncodeLocal = AudioEncode(
            codec = "aac",
            bitrate = "72000",
            samplerate = 48000,
            channels = 2,
            params = linkedMapOf("profile:a" to "LC", "cutoff" to "14000"),
            filters = listOf("1", "3"),
            audioMixPreset = "de",
            skipIfNoAudioMixPreset = true
        )

        val output = audioEncodeLocal.getOutput(
            videoFile = videoFile.copy(audioStreams = listOf(getAudioStream(6))),
            outputFolder = "/some/output/folder",
            debugOverlay = false,
            thumbnailTime = null,
            audioMixPresets = mapOf("de" to AudioMixPreset(fallbackToAuto = false))
        )
        assertThat(output).isNull()
    }

    private fun getAudioStream(channelCount: Int) = AudioStream(
        format = "format",
        codec = "aac",
        duration = 10.0,
        channels = channelCount,
        samplingRate = 23123,
        bitrate = 213123
    )
}
