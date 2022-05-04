// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode

interface VideoEncode : OutputProducer {
    val width: Int?
    val height: Int?
    val twoPass: Boolean
    val params: Map<String, String>
    val filters: List<String>?
    val audioEncode: AudioEncode?
    val audioEncodes: List<AudioEncode>
    val suffix: String
    val format: String
    val codec: String
    val inputLabel: String

    override fun getOutput(job: EncoreJob, audioMixPresets: Map<String, AudioMixPreset>): Output? {
        val audioEncodesToUse = audioEncodes.ifEmpty { listOfNotNull(audioEncode) }
        val audio = audioEncodesToUse.flatMap { it.getOutput(job, audioMixPresets)?.audioStreams.orEmpty() }
        return Output(
            id = "$suffix.$format",
            video = VideoStreamEncode(
                params = secondPassParams().toParams(),
                firstPassParams = firstPassParams().toParams(),
                inputLabels = listOf(inputLabel),
                twoPass = twoPass,
                filter = videoFilter(job.debugOverlay),
            ),
            audioStreams = audio,
            output = "${job.baseName}$suffix.$format"
        )
    }

    fun firstPassParams(): Map<String, String> {
        return if (!twoPass) {
            emptyMap()
        } else params + Pair("c:v", codec) + passParams(1)
    }

    fun secondPassParams(): Map<String, String> {
        return if (!twoPass) {
            params + Pair("c:v", codec)
        } else params + Pair("c:v", codec) + passParams(2)
    }

    fun passParams(pass: Int): Map<String, String> =
        mapOf("pass" to pass.toString(), "passlogfile" to "log$suffix")

    private fun videoFilter(debugOverlay: Boolean): String? {
        val videoFilters = mutableListOf<String>()
        if (width != null && height != null) {
            videoFilters.add("scale=$width:$height:force_original_aspect_ratio=decrease:force_divisible_by=2")
        } else if (width != null || height != null) {
            videoFilters.add("scale=${width ?: -2}:${height ?: -2}")
        }
        filters?.let { videoFilters.addAll(it) }
        if (debugOverlay) {
            videoFilters.add("drawtext=text=$suffix:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10")
        }
        return if (videoFilters.isEmpty()) null else videoFilters.joinToString(",")
    }
}
