// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.input.analyzedVideo
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.toFractionOrNull
import kotlin.math.round

data class ThumbnailEncode(
    val percentages: List<Int> = listOf(25, 50, 75),
    val thumbnailWidth: Int = -2,
    val thumbnailHeight: Int = 1080,
    val quality: Int = 5,
    val suffix: String = "_thumb",
    val inputLabel: String = DEFAULT_VIDEO_LABEL,
    val optional: Boolean = false
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(job: EncoreJob, audioMixPresets: Map<String, AudioMixPreset>): Output? {
        val videoStream = job.inputs.analyzedVideo(inputLabel)?.highestBitrateVideoStream
            ?: return logOrThrow("Can not produce thumbnail $suffix. No video input with label $inputLabel!")

        val frameRate = videoStream.frameRate.toFractionOrNull()?.toDouble()
            ?: if (job.duration != null || job.seekTo != null || job.thumbnailTime != null) {
                return logOrThrow("Can not produce thumbnail $suffix! No framerate detected in video input $inputLabel.")
            } else 0.0

        val numFrames = job.duration?.let { round(it * frameRate).toInt() } ?: videoStream.numFrames
        val skipFrames = job.seekTo?.let { round(it * frameRate).toInt() } ?: 0
        val frames = job.thumbnailTime?.let {
            listOf(round(it * frameRate).toInt())
        } ?: percentages.map {
            (it * numFrames) / 100 + skipFrames
        }

        val filter = frames.joinToString(
            separator = "+",
            prefix = "select=",
            postfix = ",scale=$thumbnailWidth:$thumbnailHeight"
        ) { "eq(n\\,$it)" }

        val fileRegex = Regex("${job.baseName}$suffix\\d{2}\\.jpg")
        val params = linkedMapOf(
            "frames:v" to "${frames.size}",
            "vsync" to "vfr",
            "q:v" to "$quality"
        )

        return Output(
            id = "${suffix}02d.jpg",
            video = VideoStreamEncode(
                params = params.toParams(),
                filter = filter,
                inputLabels = listOf(inputLabel)
            ),
            output = "${job.baseName}$suffix%02d.jpg",
            postProcessor = { outputFolder ->
                outputFolder.listFiles().orEmpty().filter { it.name.matches(fileRegex) }
            },
            seekable = false
        )
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
