// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.input.videoInput
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.FractionString
import se.svt.oss.mediaanalyzer.file.stringValue
import se.svt.oss.mediaanalyzer.file.toFraction
import se.svt.oss.mediaanalyzer.file.toFractionOrNull
import kotlin.math.absoluteValue

private val log = KotlinLogging.logger { }

interface VideoEncode : OutputProducer {
    val width: Int?
    val height: Int?
    val twoPass: Boolean
    val params: Map<String, String>
    val filters: List<String>?
    val audioEncode: AudioEncoder?
    val audioEncodes: List<AudioEncoder>
    val suffix: String
    val format: String
    val codec: String
    val inputLabel: String
    val optional: Boolean
    override val enabled: Boolean
    val cropTo: FractionString?
    val padTo: FractionString?

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        if (!enabled) {
            log.info { "Encode $suffix is not enabled. Skipping." }
            return null
        }
        val audioEncodesToUse = audioEncodes.ifEmpty { listOfNotNull(audioEncode) }
        val audio = audioEncodesToUse.flatMap { it.getOutput(job, encodingProperties)?.audioStreams.orEmpty() }
        val videoInput = job.inputs.videoInput(inputLabel)
            ?: return logOrThrow("No valid video input with label $inputLabel!")
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
            output = "${job.baseName}$suffix.$format",
        )
    }

    fun firstPassParams(): Map<String, String> = if (!twoPass) {
        emptyMap()
    } else {
        params + Pair("c:v", codec) + passParams(1)
    }

    fun secondPassParams(): Map<String, String> = if (!twoPass) {
        params + Pair("c:v", codec)
    } else {
        params + Pair("c:v", codec) + passParams(2)
    }

    fun passParams(pass: Int): Map<String, String> =
        mapOf("pass" to pass.toString(), "passlogfile" to "log$suffix")

    fun videoFilter(
        debugOverlay: Boolean,
        encodingProperties: EncodingProperties,
        videoInput: VideoIn,
    ): String? {
        val videoFilters = mutableListOf<String>()
        cropTo?.toFraction()?.let {
            videoFilters.add("crop=min(iw\\,ih*${it.stringValue()}):min(ih\\,iw/(${it.stringValue()}))")
        }
        var scaleToWidth = width
        var scaleToHeight = height
        val videoStream = videoInput.analyzedVideo.highestBitrateVideoStream
        val isRotated90 = videoStream.rotation?.rem(180)?.absoluteValue == 90
        val outputDar = (padTo ?: cropTo ?: videoInput.padTo ?: videoInput.cropTo)?.toFractionOrNull()
            ?: (videoStream.displayAspectRatio?.toFractionOrNull() ?: Fraction(videoStream.width, videoStream.height))
                .let { if (isRotated90) it.reciprocal() else it }
        val outputIsPortrait = outputDar < Fraction.ONE
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
        padTo?.toFraction()?.let {
            videoFilters.add("pad=aspect=${it.stringValue()}:x=(ow-iw)/2:y=(oh-ih)/2")
        }
        filters?.let { videoFilters.addAll(it) }
        if (debugOverlay) {
            videoFilters.add("drawtext=text=$suffix:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10")
        }
        return if (videoFilters.isEmpty()) null else videoFilters.joinToString(",")
    }

    fun logOrThrow(message: String): Output? {
        if (optional) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
