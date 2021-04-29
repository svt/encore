// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.apache.commons.io.FilenameUtils
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.mediafile.audioLayout
import se.svt.oss.encore.model.mediafile.channelCount
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output
import se.svt.oss.mediaanalyzer.file.VideoFile

data class AudioEncode(
    val codec: String = "libfdk_aac",
    val bitrate: String? = null,
    val samplerate: Int = 48000,
    val channels: Int = 2,
    val suffix: String = "${codec}_${channels}ch",
    val params: LinkedHashMap<String, String> = linkedMapOf(),
    val filters: List<String> = emptyList(),
    val audioMixPreset: String = "default",
    val skipIfNoAudioMixPreset: Boolean = false,
    val format: String = "mp4"
) : OutputProducer {

    override fun getOutput(
        videoFile: VideoFile,
        outputFolder: String,
        debugOverlay: Boolean,
        thumbnailTime: Int?,
        audioMixPresets: Map<String, AudioMixPreset>
    ): Output? {
        if (videoFile.audioLayout() == AudioLayout.INVALID) {
            throw RuntimeException("Audio layout of input is not supported! ")
        }
        val paramsList = mutableListOf(
            "-c:a",
            codec,
            "-ar",
            "$samplerate"
        )
        val inputChannels = videoFile.channelCount()
        val preset = audioMixPresets[audioMixPreset] ?: throw RuntimeException("Audio mix preset $audioMixPreset not found!")
        val pan = preset.panMapping[inputChannels]?.get(channels)
            ?: if (channels <= inputChannels) preset.defaultPan[channels] else null

        if (pan == null) {
            when {
                preset.fallbackToAuto && isApplicable(inputChannels) -> paramsList.addAll(0, listOf("-ac", "$channels"))
                skipIfNoAudioMixPreset -> return null
                else -> throw RuntimeException("No audio mix preset for $audioMixPreset: $inputChannels -> $channels channels!")
            }
        }

        bitrate?.let {
            paramsList.add("-b:a")
            paramsList.add(it)
        }

        params.forEach {
            paramsList.add("-${it.key}")
            paramsList.add(it.value)
        }

        return Output(
            null,
            AudioStreamEncode(paramsList, filtersToString(pan)),
            getOutputFilename(videoFile.file, outputFolder)
        )
    }

    private fun filtersToString(pan: String?): String? {
        val allFilters = pan?.let { listOf("pan=$it") + filters } ?: filters
        return if (allFilters.isEmpty()) null else allFilters.joinToString(",")
    }

    private fun isApplicable(channelCount: Int): Boolean {
        return channelCount > 0 && (channels == 2 || channels in 1..channelCount)
    }

    private fun getOutputFilename(filename: String, folder: String) =
        "$folder/${FilenameUtils.getBaseName(filename)}_$suffix.$format"
}
