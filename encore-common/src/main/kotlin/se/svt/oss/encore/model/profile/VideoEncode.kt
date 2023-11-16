// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.input.videoInput
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.toFractionOrNull

abstract class VideoEncode : OutputProducer {
    abstract val width: Int?
    abstract val height: Int?
    abstract val twoPass: Boolean
    abstract val params: Map<String, String>
    abstract val filters: List<String>?
    abstract val audioEncode: AudioEncoder?
    abstract val audioEncodes: List<AudioEncoder>
    abstract val suffix: String
    abstract val format: String
    abstract val codec: String
    abstract val inputLabel: String

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        val audioEncodesToUse = audioEncodes.ifEmpty { listOfNotNull(audioEncode) }
        val audio = audioEncodesToUse.flatMap { it.getOutput(job, encodingProperties)?.audioStreams.orEmpty() }
        val videoInput = job.inputs.videoInput(inputLabel)
            ?: throw RuntimeException("No valid video input with label $inputLabel!")
        return Output(
            id = "$suffix.$format",
            video = VideoStreamEncode(
                params = secondPassParams().toParams(),
                firstPassParams = firstPassParams().toParams(),
                inputLabels = listOf(inputLabel),
                twoPass = twoPass,
                filter = videoFilter(job.debugOverlay, encodingProperties, videoInput),
            ),
            audioStreams = audio,
            output = "${job.baseName}$suffix.$format"
        )
    }

    fun firstPassParams(): Map<String, String> {
        return if (!twoPass) {
            emptyMap()
        } else {
            params + Pair("c:v", codec) + passParams(1)
        }
    }

    fun secondPassParams(): Map<String, String> {
        return if (!twoPass) {
            params + Pair("c:v", codec)
        } else {
            params + Pair("c:v", codec) + passParams(2)
        }
    }

    open fun passParams(pass: Int): Map<String, String> =
        mapOf("pass" to pass.toString(), "passlogfile" to "log$suffix")

    private fun videoFilter(
        debugOverlay: Boolean,
        encodingProperties: EncodingProperties,
        videoInput: VideoIn
    ): String? {
        val videoFilters = mutableListOf<String>()
        var scaleToWidth = width
        var scaleToHeight = height
        val videoStream = videoInput.analyzedVideo.highestBitrateVideoStream
        val outputDar = (videoInput.padTo ?: videoInput.cropTo ?: videoStream.displayAspectRatio)?.toFractionOrNull()
        val outputIsPortrait = outputDar != null && outputDar < Fraction.ONE
        val isScalingWithinLandscape =
            scaleToWidth != null && scaleToHeight != null && Fraction(scaleToWidth, scaleToHeight) > Fraction.ONE
        if (encodingProperties.flipWidthHeightIfPortrait && outputIsPortrait && isScalingWithinLandscape) {
            scaleToWidth = height
            scaleToHeight = width
        }
        if (scaleToWidth != null && scaleToHeight != null) {
            videoFilters.add("scale=$scaleToWidth:$scaleToHeight:force_original_aspect_ratio=decrease:force_divisible_by=2")
            videoFilters.add("setsar=1/1")
        } else if (scaleToWidth != null || scaleToHeight != null) {
            videoFilters.add("scale=${scaleToWidth ?: -2}:${scaleToHeight ?: -2}")
        }
        filters?.let { videoFilters.addAll(it) }
        if (debugOverlay) {
            videoFilters.add("drawtext=text=$suffix:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10")
        }
        return if (videoFilters.isEmpty()) null else videoFilters.joinToString(",")
    }
}
