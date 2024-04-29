// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.input.analyzedVideo
import se.svt.oss.encore.model.input.videoInput
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.encore.process.createTempDir
import se.svt.oss.mediaanalyzer.file.stringValue

data class ThumbnailMapEncode(
    val tileWidth: Int = 160,
    val tileHeight: Int = 90,
    val cols: Int = 12,
    val rows: Int = 20,
    val optional: Boolean = true,
    val suffix: String = "_${cols}x${rows}_${tileWidth}x${tileHeight}_thumbnail_map",
    val format: String = "jpg",
    val inputLabel: String = DEFAULT_VIDEO_LABEL
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output? {
        if (job.segmentLength != null) {
            return logOrThrow("Thumbnail map is not supported in segmented encode!")
        }
        val videoInput = job.inputs.videoInput(inputLabel)
        val inputSeekTo = videoInput?.seekTo
        val videoStream = job.inputs.analyzedVideo(inputLabel)?.highestBitrateVideoStream
            ?: return logOrThrow("No input with label $inputLabel!")

        var inputDuration = videoStream.duration
        inputSeekTo?.let { inputDuration -= it }
        job.seekTo?.let { inputDuration -= it }
        val outputDuration = job.duration ?: inputDuration

        if (outputDuration <= 0) {
            return logOrThrow("Cannot create thumbnail map $suffix! Could not detect duration.")
        }

        val interval = outputDuration / (cols * rows)
        val select = job.seekTo
            ?.let { "gte(t\\,$it)*(isnan(prev_selected_t)+gt(floor((t-$it)/$interval)\\,floor((prev_selected_t-$it)/$interval)))" }
            ?: "isnan(prev_selected_t)+gt(floor(t/$interval)\\,floor(prev_selected_t/$interval))"

        val tempFolder = createTempDir(suffix).toFile()
        tempFolder.deleteOnExit()

        val pad = "aspect=${Fraction(tileWidth, tileHeight).stringValue()}:x=(ow-iw)/2:y=(oh-ih)/2"

        val scale = if (format == "jpg") {
            "-1:$tileHeight:out_range=jpeg"
        } else {
            "-1:$tileHeight"
        }
        val params = linkedMapOf(
            "q:v" to "5",
            "fps_mode" to "vfr"
        )
        return Output(
            id = "$suffix.$format",
            video = VideoStreamEncode(
                params = params.toParams(),
                filter = "select=$select,pad=$pad,scale=$scale",
                inputLabels = listOf(inputLabel)
            ),
            output = tempFolder.resolve("${job.baseName}$suffix%04d.$format").toString(),
            postProcessor = { outputFolder ->
                try {
                    val targetFile = outputFolder.resolve("${job.baseName}$suffix.$format")
                    val process = ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-i",
                        "${job.baseName}$suffix%04d.$format",
                        "-vf",
                        "tile=${cols}x$rows",
                        "-frames:v",
                        "1",
                        "$targetFile"
                    )
                        .directory(tempFolder)
                        .start()
                    val status = process.waitFor()
                    tempFolder.deleteRecursively()
                    if (status != 0) {
                        throw RuntimeException("Ffmpeg returned status code $status")
                    }
                    listOf(targetFile)
                } catch (e: Exception) {
                    logOrThrow("Error creating thumbnail map! ${e.message}")
                    emptyList()
                }
            },
            isImage = true
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
