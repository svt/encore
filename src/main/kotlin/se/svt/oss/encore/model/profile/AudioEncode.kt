// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.analyzedAudio
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.mediafile.audioLayout
import se.svt.oss.encore.model.mediafile.channelCount
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output

data class AudioEncode(
    val codec: String = "libfdk_aac",
    val bitrate: String? = null,
    val samplerate: Int = 48000,
    val channels: Int = 2,
    val suffix: String = "_${codec}_${channels}ch",
    val params: LinkedHashMap<String, String> = linkedMapOf(),
    val filters: List<String> = emptyList(),
    val audioMixPreset: String = "default",
    val optional: Boolean = false,
    val format: String = "mp4",
    val inputLabel: String = DEFAULT_AUDIO_LABEL,
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(job: EncoreJob, audioMixPresets: Map<String, AudioMixPreset>): Output? {
        val outputName = "${job.baseName}$suffix.$format"
        val analyzed = job.inputs.analyzedAudio(inputLabel)
            ?: return logOrThrow("Can not generate $outputName! No audio input with label '$inputLabel'.")
        if (analyzed.audioLayout() == AudioLayout.INVALID) {
            throw RuntimeException("Audio layout of audio input '$inputLabel' is not supported!")
        }
        val inputChannels = analyzed.channelCount()
        val preset = audioMixPresets[audioMixPreset]
            ?: throw RuntimeException("Audio mix preset '$audioMixPreset' not found!")
        val pan = preset.panMapping[inputChannels]?.get(channels)
            ?: if (channels <= inputChannels) preset.defaultPan[channels] else null

        val outParams = linkedMapOf<String, Any>()
        if (pan == null) {
            if (preset.fallbackToAuto && isApplicable(inputChannels)) {
                outParams["ac:a:{stream_index}"] = channels
            } else {
                return logOrThrow("Can not generate $outputName! No audio mix preset for '$audioMixPreset': $inputChannels -> $channels channels!")
            }
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
                    filter = filtersToString(pan)
                )
            ),
            output = outputName,
        )
    }

    private fun filtersToString(pan: String?): String? {
        val allFilters = pan?.let { listOf("pan=$it") + filters } ?: filters
        return if (allFilters.isEmpty()) null else allFilters.joinToString(",")
    }

    private fun isApplicable(channelCount: Int): Boolean {
        return channelCount > 0 && (channels == 2 || channels in 1..channelCount)
    }

    private fun logOrThrow(message: String): Output? {
        if (optional) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
