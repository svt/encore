// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
import se.svt.oss.encore.model.input.analyzedAudio
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output

data class SimpleAudioEncode(
    val codec: String = "libfdk_aac",
    val bitrate: String? = null,
    val samplerate: Int? = null,
    val suffix: String = "_$codec",
    val params: LinkedHashMap<String, String> = linkedMapOf(),
    override val optional: Boolean = false,
    val format: String = "mp4",
    val inputLabel: String = DEFAULT_AUDIO_LABEL,
) : AudioEncoder() {
    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        val outputName = "${job.baseName}$suffix.$format"
        job.inputs.analyzedAudio(inputLabel)
            ?: return logOrThrow("Can not generate $outputName! No audio input with label '$inputLabel'.")
        val outParams = linkedMapOf<String, Any>()
        outParams += params
        outParams["c:a"] = codec
        samplerate?.let { outParams["ar"] = it }
        bitrate?.let { outParams["b:a"] = it }

        return Output(
            id = "$suffix.$format",
            video = null,
            audioStreams = listOf(
                AudioStreamEncode(
                    params = outParams.toParams(),
                    inputLabels = listOf(inputLabel),
                    preserveLayout = true,
                ),
            ),
            output = outputName,
        )
    }
}
