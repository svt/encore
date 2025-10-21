// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
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
    val dialogueEnhancement: DialogueEnhancement = DialogueEnhancement(),
    override val optional: Boolean = false,
    val format: String = "mp4",
    val inputLabel: String = DEFAULT_AUDIO_LABEL,
    override val enabled: Boolean = true,
) : AudioEncoder() {

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        val outputName = "${job.baseName}$suffix.$format"
        if (!enabled) {
            return logOrThrow("$outputName is disabled. Skipping...")
        }
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
        var inputChannelLayout = audioIn.channelLayout(encodingProperties.defaultChannelLayouts)
        if (dialogueEnhancement.enabled && !dialogueEnhancePossible(inputChannelLayout)) {
            return logOrThrow("Can not generate $outputName! Dialogue enhancement not possible for source channel layout ${inputChannelLayout.layoutName}")
        }

        val dialogueEnhanceFilters = mutableListOf<String>()
        if (shouldDialogueEnhanceStereo(inputChannelLayout)) {
            inputChannelLayout = ChannelLayout.CH_LAYOUT_3POINT0
            dialogueEnhanceFilters.add(dialogueEnhancement.dialogueEnhanceStereo.filterString)
        }
        if (dialogueEnhancement.enabled) {
            dialogueEnhanceFilters.add(dialogueEnhance(inputChannelLayout))
        }

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
                    filter = (dialogueEnhanceFilters + mixFilters + filters).joinToString(",").ifEmpty { null },
                ),
            ),
            output = outputName,
        )
    }

    private fun shouldDialogueEnhanceStereo(inputChannelLayout: ChannelLayout): Boolean =
        dialogueEnhancement.enabled &&
            dialogueEnhancement.dialogueEnhanceStereo.enabled &&
            inputChannelLayout == ChannelLayout.CH_LAYOUT_STEREO

    private fun dialogueEnhancePossible(inputChannelLayout: ChannelLayout): Boolean =
        inputChannelLayout.channels.size > 1 &&
            (inputChannelLayout.channels.contains(ChannelId.FC) || shouldDialogueEnhanceStereo(inputChannelLayout))

    private fun dialogueEnhance(inputChannelLayout: ChannelLayout): String {
        val layoutName = inputChannelLayout.layoutName
        val channels = inputChannelLayout.channels
        val channelSplit =
            "channelsplit=channel_layout=$layoutName${channels.joinToString(separator = "") { "[CH-$suffix-$it]" }}"
        val centerSplit = "[CH-$suffix-FC]asplit=2[SC-$suffix][CH-$suffix-FC-OUT]"
        val bgChannels = channels - ChannelId.FC
        val bgMerge =
            "${bgChannels.joinToString(separator = "") { "[CH-$suffix-$it]" }}amerge=inputs=${bgChannels.size}[BG-$suffix]"
        val compress =
            "[BG-$suffix][SC-$suffix]${dialogueEnhancement.sidechainCompress.filterString}[COMPR-$suffix]"
        val mixMerge = "[COMPR-$suffix][CH-$suffix-FC-OUT]amerge"
        return listOf(channelSplit, centerSplit, bgMerge, compress, mixMerge).joinToString(";")
    }

    private fun isApplicable(channelCount: Int): Boolean = channelCount > 0 && (channelLayout == ChannelLayout.CH_LAYOUT_STEREO || channelLayout.channels.size in 1..channelCount)

    data class DialogueEnhancement(
        val enabled: Boolean = false,
        val sidechainCompress: SidechainCompress = SidechainCompress(),
        val dialogueEnhanceStereo: DialogueEnhanceStereo = DialogueEnhanceStereo(),
    ) {
        data class DialogueEnhanceStereo(
            val enabled: Boolean = true,
            val original: Int = 1,
            val enhance: Int = 1,
            val voice: Int = 2,
        ) {
            val filterString: String
                get() = "dialoguenhance=original=$original:enhance=$enhance:voice=$voice"
        }

        data class SidechainCompress(
            val ratio: Int = 8,
            val threshold: Double = 0.012,
            val release: Double = 1000.0,
            val attack: Double = 100.0,
        ) {
            val filterString: String
                get() = "sidechaincompress=threshold=$threshold:ratio=$ratio:release=$release:attack=$attack"
        }
    }
}
