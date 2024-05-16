// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.input.videoInput
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode

data class ThumbnailEncode(
    val percentages: List<Int> = listOf(25, 50, 75),
    val thumbnailWidth: Int = -2,
    val thumbnailHeight: Int = 1080,
    val quality: Int = 5,
    val suffix: String = "_thumb",
    val suffixZeroPad: Int = 2,
    val inputLabel: String = DEFAULT_VIDEO_LABEL,
    val optional: Boolean = false,
    val intervalSeconds: Double? = null,
    val decodeOutput: Int? = null
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        if (job.segmentLength != null) {
            return logOrThrow("Thumbnail is not supported in segmented encode!")
        }
        val videoInput = job.inputs.videoInput(inputLabel)
            ?: return logOrThrow("Can not produce thumbnail $suffix. No video input with label $inputLabel!")
        val thumbnailTime = job.thumbnailTime?.let { time ->
            videoInput.seekTo?.let { time - it } ?: time
        }
        val select = when {
            thumbnailTime != null -> selectTimes(listOf(thumbnailTime))
            intervalSeconds != null -> selectInterval(intervalSeconds, job.seekTo)
            outputDuration(videoInput, job) <= 0 -> return logOrThrow("Can not produce thumbnail $suffix. Could not detect duration.")
            percentages.isNotEmpty() -> selectTimes(percentagesToTimes(videoInput, job))
            else -> return logOrThrow("Can not produce thumbnail $suffix. No times selected.")
        }

        val filter = "$select,scale=w=$thumbnailWidth:h=$thumbnailHeight:out_range=jpeg"
        val params = linkedMapOf(
            "fps_mode" to "vfr",
            "q:v" to "$quality"
        )

        val fileRegex = Regex("${job.baseName}$suffix\\d{$suffixZeroPad}\\.jpg")

        return Output(
            id = "${suffix}0${suffixZeroPad}d.jpg",
            video = VideoStreamEncode(
                params = params.toParams(),
                filter = filter,
                inputLabels = listOf(inputLabel),
            ),
            output = "${job.baseName}$suffix%0${suffixZeroPad}d.jpg",
            postProcessor = { outputFolder ->
                outputFolder.listFiles().orEmpty().filter { it.name.matches(fileRegex) }
            },
            isImage = true,
            decodeOutputStream = decodeOutput?.let { "$it:v:0" }
        )
    }

    private fun selectInterval(interval: Double, outputSeek: Double?): String {
        val select = if (outputSeek != null && decodeOutput == null) {
            "gte(t\\,$outputSeek)*(isnan(prev_selected_t)+gt(floor((t-$outputSeek)/$interval)\\,floor((prev_selected_t-$outputSeek)/$interval)))"
        } else {
            "isnan(prev_selected_t)+gt(floor(t/$interval)\\,floor(prev_selected_t/$interval))"
        }
        return "select=$select"
    }

    private fun outputDuration(videoIn: VideoIn, job: EncoreJob): Double {
        val videoStream = videoIn.analyzedVideo.highestBitrateVideoStream
        var inputDuration = videoStream.duration
        videoIn.seekTo?.let { inputDuration -= it }
        job.seekTo?.let { inputDuration -= it }
        return job.duration ?: inputDuration
    }

    private fun percentagesToTimes(videoIn: VideoIn, job: EncoreJob): List<Double> {
        val outputDuration = outputDuration(videoIn, job)
        return percentages
            .map { it * outputDuration / 100 }
            .map { t ->
                val outputSeek = job.seekTo
                if (outputSeek != null && decodeOutput == null) {
                    t + outputSeek
                } else {
                    t
                }
            }
    }

    private fun selectTimes(times: List<Double>) =
        "select=${times.joinToString("+") { "lt(prev_pts*TB\\,$it)*gte(pts*TB\\,$it)" }}"

    private fun logOrThrow(message: String): Output? {
        if (optional) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
