// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.config.EncodingProperties
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
        channelLayout = ChannelLayout.CH_LAYOUT_STEREO,
    )

    private val dePreset = AudioMixPreset(
        fallbackToAuto = false,
        defaultPan = mapOf(
            ChannelLayout.CH_LAYOUT_STEREO to "FL<FL+1.5*FC+0.707107*BL+0.707107*SL|FR<FR+1.5*FC+0.707107*BR+0.707107*SR",
        ),
        panMapping = mapOf(
            ChannelLayout.CH_LAYOUT_MONO to mapOf(
                ChannelLayout.CH_LAYOUT_STEREO to "FL=0.707*FC|FR=0.707*FC",
            ),
            ChannelLayout.CH_LAYOUT_5POINT1 to mapOf(
                ChannelLayout.CH_LAYOUT_5POINT1 to "c0=c0|c1=c1|c2<1.5*c2|c3=c3|c4=c4|c5=c5",
            ),
        ),
    )

    private val encodingProperties = EncodingProperties(
        audioMixPresets = mapOf(
            "default" to AudioMixPreset(),
            "de" to dePreset,
        ),
    )

    private val videoFile = defaultVideoFile

    @Test
    fun `no audio streams throws exception`() {
        assertThatThrownBy {
            audioEncode.getOutput(job(), encodingProperties)
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No audio streams in input")
    }

    @Test
    fun `not supported soundtype throws exception`() {
        val job = job(getAudioStream(1), getAudioStream(2))
        assertThatThrownBy {
            audioEncode.getOutput(
                job,
                EncodingProperties(audioMixPresets = mapOf("default" to AudioMixPreset(fallbackToAuto = false))),
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Audio layout of audio input 'main' is not supported!")
    }

    @Test
    fun `valid output`() {
        val output = audioEncode.getOutput(
            job(getAudioStream(6)),
            encodingProperties,
        )
        assertThat(output)
            .hasOutput("test_aac_stereo.mp4")
            .hasVideo(null)
            .hasId("_aac_stereo.mp4")
            .hasOnlyAudioStreams(
                AudioStreamEncode(
                    params = listOf("-c:a:{stream_index}", "aac", "-ar:a:{stream_index}", "48000"),
                    inputLabels = listOf(DEFAULT_AUDIO_LABEL),
                    filter = "aformat=channel_layouts=stereo",
                ),
            )
    }

    @Test
    fun `returns null when not enabled`() {
        val output = audioEncode.copy(enabled = false)
            .getOutput(job(getAudioStream(6)), encodingProperties)
        assertThat(output).isNull()
    }

    @Test
    fun `valid output all values set`() {
        val audioEncodeLocal = AudioEncode(
            codec = "aac",
            bitrate = "72000",
            samplerate = 48000,
            channelLayout = ChannelLayout.CH_LAYOUT_STEREO,
            params = linkedMapOf("profile:a" to "LC", "cutoff" to "14000"),
            filters = listOf("1", "3"),
        )

        val output = audioEncodeLocal.getOutput(
            job = job(getAudioStream(6)),
            encodingProperties = EncodingProperties(
                audioMixPresets = mapOf(
                    "default" to AudioMixPreset(
                        panMapping = mapOf(
                            ChannelLayout.CH_LAYOUT_5POINT1 to mapOf(ChannelLayout.CH_LAYOUT_STEREO to "c0=c0|c1=c1"),
                        ),
                    ),
                ),
            ),
        )
        assertThat(output)
            .hasOutput("test_aac_stereo.mp4")
            .hasVideo(null)
            .hasOnlyAudioStreams(
                AudioStreamEncode(
                    listOf(
                        "-c:a:{stream_index}",
                        "aac",
                        "-ar:a:{stream_index}",
                        "48000",
                        "-b:a:{stream_index}",
                        "72000",
                        "-profile:a",
                        "LC",
                        "-cutoff",
                        "14000",
                    ),
                    "pan=stereo|c0=c0|c1=c1,1,3",
                    listOf(
                        DEFAULT_AUDIO_LABEL,
                    ),
                ),
            )
    }

    @Test
    fun `valid output with no matching audio preset optional gives null`() {
        val audioEncodeLocal = audioEncode.copy(
            audioMixPreset = "de",
            optional = true,
        )

        val output = audioEncodeLocal.getOutput(
            job = job(getAudioStream(6)),
            encodingProperties = EncodingProperties(
                audioMixPresets = mapOf(
                    "de" to AudioMixPreset(
                        fallbackToAuto = false,
                    ),
                ),
            ),
        )
        assertThat(output).isNull()
    }

    @Test
    fun `valid output with no matching audio preset not optional throws`() {
        val audioEncodeLocal = audioEncode.copy(
            audioMixPreset = "de",
        )

        assertThatThrownBy {
            audioEncodeLocal.getOutput(
                job(getAudioStream(6)),
                encodingProperties = EncodingProperties(
                    audioMixPresets = mapOf(
                        "de" to AudioMixPreset(
                            fallbackToAuto = false,
                        ),
                    ),
                ),
            )
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("No audio mix preset for 'de': 5.1 -> stereo")
    }

    @Test
    fun `unmapped input optional returns null`() {
        val audioEncodeLocal = audioEncode.copy(inputLabel = "other", optional = true)
        val output = audioEncodeLocal.getOutput(job(getAudioStream(6)), encodingProperties)
        assertThat(output).isNull()
    }

    @Test
    fun `unmapped input not optional throws`() {
        val audioEncodeLocal = audioEncode.copy(inputLabel = "other")
        assertThatThrownBy {
            audioEncodeLocal.getOutput(job(getAudioStream(6)), encodingProperties)
        }.isInstanceOf(RuntimeException::class.java)
            .hasMessage("Can not generate test_aac_stereo.mp4! No audio input with label 'other'.")
    }

    @Test
    fun `dialogue enhance Native stereo input emits builtin dialoguenhance filter`() {
        val encode = stereoDialogueEnhance(DialogueEnhancement.Native(enabled = true))
        val filter = encode.getOutput(job(getAudioStream(2)), encodingProperties)!!
            .audioStreams.single().filter

        assertThat(filter)
            .contains("dialoguenhance=original=1:enhance=1:voice=2,channelsplit=channel_layout=3.0")
            .contains("sidechaincompress")
            .contains(dePreset.defaultPan[ChannelLayout.CH_LAYOUT_STEREO]!!)
            .doesNotContain("dnenhance")
    }

    @Test
    fun `dialogue enhance Dn stereo input emits pan-dnenhance-join bridge to 3 dot 0`() {
        val encode = stereoDialogueEnhance(DialogueEnhancement.Dn(enabled = true))
        val filter = encode.getOutput(job(getAudioStream(2)), encodingProperties)!!
            .audioStreams.single().filter
        assertThat(filter)
            .contains("pan=mono|c0=0.707*c0+0.707*c1")
            .contains("join=inputs=2:channel_layout=3.0,channelsplit=channel_layout=3.0")
            .contains("[CH-_aac_stereo-FC]dnenhance,asplit=2")
            .contains(dePreset.defaultPan[ChannelLayout.CH_LAYOUT_STEREO]!!)
            .doesNotContain("dialoguenhance")
    }

    @Test
    fun `dialogue enhance Dn 5dot1 input inserts dnenhance on FC channel`() {
        val encode = surroundDialogueEnhance(DialogueEnhancement.Dn(enabled = true))
        val filter = encode.getOutput(job(getAudioStream(6)), encodingProperties)!!
            .audioStreams.single().filter
        assertThat(filter)
            .contains("[CH-_aac_5.1-FC]dnenhance,asplit=2")
            .contains("sidechaincompress")
            .contains(dePreset.panMapping[ChannelLayout.CH_LAYOUT_5POINT1]!![ChannelLayout.CH_LAYOUT_5POINT1]!!)
    }

    @Test
    fun `dialogue enhance Dn passes non-default filter options through to dnenhance call`() {
        val encode = stereoDialogueEnhance(
            DialogueEnhancement.Dn(
                enabled = true,
                model = "/some/where/DFN3-LL.tar.gz",
                lookahead = 0,
                postFilter = false,
                attenuationLimit = 50.0,
            ),
        )
        val filter = encode.getOutput(job(getAudioStream(2)), encodingProperties)!!
            .audioStreams.single().filter

        assertThat(filter).contains("dnenhance=model=/some/where/DFN3-LL.tar.gz:post_filter=0:attenuation_limit=50.0:lookahead=0")
    }

    @Test
    fun `dialogue enhance Dn mono input bridges to 3 dot 0 and ducks original against cleaned FC`() {
        val encode = stereoDialogueEnhance(DialogueEnhancement.Dn(enabled = true))

        val filter = encode.getOutput(
            job(getAudioStream(1)),
            encodingProperties,
        )!!.audioStreams.single().filter

        assertThat(filter)
            .contains("pan=stereo|c0=0.707*c0|c1=0.707*c0")
            .contains("join=inputs=2:channel_layout=3.0,channelsplit=channel_layout=3.0")
            .contains("[CH-_aac_stereo-FC]dnenhance,asplit=2")
            .contains("sidechaincompress")
            .contains(dePreset.defaultPan[ChannelLayout.CH_LAYOUT_STEREO]!!)
            .doesNotContain("dialoguenhance")
    }

    @Test
    fun `dialogue enhance Native mono input is rejected`() {
        val encode = AudioEncode(
            channelLayout = ChannelLayout.CH_LAYOUT_MONO,
            audioMixPreset = "de",
            optional = true,
            dialogueEnhancement = DialogueEnhancement.Native(enabled = true),
        )

        val output = encode.getOutput(job(getAudioStream(1)), encodingProperties)

        assertThat(output).isNull()
    }

    private fun stereoDialogueEnhance(dialogueEnhancement: DialogueEnhancement) = AudioEncode(
        channelLayout = ChannelLayout.CH_LAYOUT_STEREO,
        audioMixPreset = "de",
        dialogueEnhancement = dialogueEnhancement,
    )

    private fun surroundDialogueEnhance(dialogueEnhancement: DialogueEnhancement) = AudioEncode(
        channelLayout = ChannelLayout.CH_LAYOUT_5POINT1,
        audioMixPreset = "de",
        dialogueEnhancement = dialogueEnhancement,
    )

    private fun job(vararg audioStreams: AudioStream) =
        defaultEncoreJob().copy(
            inputs = listOf(
                AudioVideoInput(
                    uri = defaultVideoFile.file,
                    analyzed = videoFile.copy(audioStreams = audioStreams.toList()),
                ),
            ),
        )

    private fun getAudioStream(channelCount: Int) = AudioStream(
        format = "format",
        codec = "aac",
        duration = 10.0,
        channels = channelCount,
        channelLayout = ChannelLayout.defaultChannelLayout(channelCount)?.layoutName,
        samplingRate = 23123,
        bitrate = 213123,
        profile = null,
    )
}
