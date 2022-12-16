// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.audioInput
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.mediafile.audioLayout
import se.svt.oss.encore.model.mediafile.channelCount
import se.svt.oss.encore.model.mediafile.channelLayout
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output

data class AudioEncode(
    val codec: String = "libfdk_aac",
    val bitrate: String? = null,
    val samplerate: Int = 48000,
    val channelLayout: ChannelLayout = ChannelLayout.CH_LAYOUT_STEREO,
    val suffix: String = "_${codec}_${channelLayout.layoutName}",
    val params: LinkedHashMap<String, String> = linkedMapOf(),
    val filters: List<String> = emptyList(),
    val audioMixPreset: String = "default",
    override val optional: Boolean = false,
    val format: String = "mp4",
    val inputLabel: String = DEFAULT_AUDIO_LABEL,
) : AudioEncoder() {

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        val outputName = "${job.baseName}$suffix.$format"
        val audioIn = job.inputs.audioInput(inputLabel)
            ?: return logOrThrow("Can not generate $outputName! No audio input with label '$inputLabel'.")
        val analyzed = audioIn.analyzedAudio
        if (analyzed.audioLayout() == AudioLayout.INVALID) {
            throw RuntimeException("Audio layout of audio input '$inputLabel' is not supported!")
        }
        if (analyzed.audioLayout() == AudioLayout.NONE) {
            return logOrThrow("Can not generate $outputName! No audio streams in input!")
        }
        val preset = encodingProperties.audioMixPresets[audioMixPreset]
            ?: throw RuntimeException("Audio mix preset '$audioMixPreset' not found!")
        val inputChannels = analyzed.channelCount()
        val inputChannelLayout = audioIn.channelLayout(encodingProperties.defaultChannelLayouts)

        val mixFilters = mutableListOf<String>()

        val pan = preset.panMapping[inputChannelLayout]?.get(channelLayout)
            ?: if (channelLayout.channels.size <= inputChannelLayout.channels.size) {
                preset.defaultPan[channelLayout]
            } else {
                null
            }

        val outParams = linkedMapOf<String, Any>()
        if (pan == null) {
            if (preset.fallbackToAuto && isApplicable(inputChannels)) {
                mixFilters.add("aformat=channel_layouts=${channelLayout.layoutName}")
            } else {
                return logOrThrow("Can not generate $outputName! No audio mix preset for '$audioMixPreset': ${inputChannelLayout.layoutName} -> ${channelLayout.layoutName}!")
            }
        } else {
            mixFilters.add("pan=${channelLayout.layoutName}|$pan")
        }
        outParams["c:a:{stream_index}"] = codec
        outParams["ar:a:{stream_index}"] = samplerate
        bitrate?.let { outParams["b:a:{stream_index}"] = it }
        outParams += params

        return Output(
            id = "$suffix.$format",
            video = null,
            audioStreams = listOf(
                AudioStreamEncode(
                    params = outParams.toParams(),
                    inputLabels = listOf(inputLabel),
                    filter = (mixFilters + filters).joinToString(",").ifEmpty { null }
                )
            ),
            output = outputName,
        )
    }

    private fun isApplicable(channelCount: Int): Boolean {
        return channelCount > 0 && (channelLayout == ChannelLayout.CH_LAYOUT_STEREO || channelLayout.channels.size in 1..channelCount)
    }
}
