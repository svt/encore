// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.io.File

data class ThumbnailMapEncode(
    val aspectWidth: Int = 16,
    val aspectHeight: Int = 9,
    val tileHeight: Int = 90,
    val cols: Int = 12,
    val rows: Int = 20
) : OutputProducer {

    private val log = KotlinLogging.logger { }

    override fun getOutput(
        videoFile: VideoFile,
        outputFolder: String,
        debugOverlay: Boolean,
        thumbnailTime: Int?,
        audioMixPresets: Map<String, AudioMixPreset>
    ): Output? {
        val numFrames = videoFile.highestBitrateVideoStream.numFrames
        if (numFrames < cols * rows) {
            log.info { "Source did not contain enough frames to generate thumbnail map: $numFrames < $cols cols * $rows rows" }
            return null
        }
        if (thumbnailTime != null) {
            log.info { "Highlight with seek (thumbnailTime set). Skip thumbnailMap" }
            return null
        }
        val pad = "aspect=$aspectWidth/$aspectHeight:x=(ow-iw)/2:y=(oh-ih)/2" // pad to aspect ratio
        val tileWidth = tileHeight * aspectWidth / aspectHeight
        val nthFrame = numFrames / (cols * rows)

        return Output(
            VideoStreamEncode(
                listOf("-frames", "1", "-q:v", "5"),
                "select=not(mod(n\\,$nthFrame)),pad=$pad,scale=-1:$tileHeight,tile=${cols}x$rows"
            ),
            null,
            getOutputFilename(videoFile.file, outputFolder, tileWidth)
        )
    }

    private fun getOutputFilename(filename: String, folder: String, tileWidth: Int) =
        "$folder/${File(filename).nameWithoutExtension}_${cols}x${rows}_${tileWidth}x${tileHeight}_thumbnail_map.jpg"
}
