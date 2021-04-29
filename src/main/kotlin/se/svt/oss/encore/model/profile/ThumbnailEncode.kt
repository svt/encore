// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.VideoFile
import se.svt.oss.mediaanalyzer.file.toFraction
import java.io.File
import kotlin.math.round

data class ThumbnailEncode(
    val percentages: List<Int> = listOf(25, 50, 75),
    val thumbnailWidth: Int = -2,
    val thumbnailHeight: Int = 1080
) : OutputProducer {

    override fun getOutput(
        videoFile: VideoFile,
        outputFolder: String,
        debugOverlay: Boolean,
        thumbnailTime: Int?,
        audioMixPresets: Map<String, AudioMixPreset>
    ): Output? {
        val videoStream = videoFile.highestBitrateVideoStream
        val filter = if (thumbnailTime == null) {
            percentages.map { (it * videoStream.numFrames) / 100 }.joinToString(
                separator = "+",
                prefix = "select=",
                postfix = ",scale=$thumbnailWidth:$thumbnailHeight"
            ) { "eq(n\\,$it)" }
        } else {
            val timeInSeconds = thumbnailTime / 1000.0
            val frameRate = videoStream.frameRate.toFraction().toDouble()
            val thumbnailFrame = round(timeInSeconds * frameRate).toInt()
            "select=eq(n\\,$thumbnailFrame),scale=$thumbnailWidth:$thumbnailHeight"
        }
        val basename = basename(videoFile, outputFolder)
        val fileRegex = Regex("${basename}_thumb\\d{2}\\.jpg")
        return Output(
            video = VideoStreamEncode(
                listOf(
                    "-vsync",
                    "vfr",
                    "-q:v",
                    "5"
                ),
                filter
            ),
            audio = null,
            output = "${basename}_thumb%02d.jpg",
            fileFilter = { file -> file.path.matches(fileRegex) }
        )
    }

    private fun basename(videoFile: VideoFile, outputFolder: String) =
        File(outputFolder).resolve(File(videoFile.file).nameWithoutExtension).path
}
