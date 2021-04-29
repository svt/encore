// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.io.File

interface VideoEncode : OutputProducer {
    val width: Int?
    val height: Int?
    val twoPass: Boolean
    val params: Map<String, String>
    val filters: List<String>?
    val audioEncode: AudioEncode?
    val suffix: String
    val format: String
    val codec: String

    private val codecWithoutPrefix
        get() = codec.removePrefix("lib")

    override fun getOutput(
        videoFile: VideoFile,
        outputFolder: String,
        debugOverlay: Boolean,
        thumbnailTime: Int?,
        audioMixPresets: Map<String, AudioMixPreset>
    ): Output? {
        val videoFilter = videoFilter(debugOverlay)
        val paramsList = (params + Pair("c:v", codec)).flatMap { listOf("-${it.key}", it.value) }
        return Output(
            VideoStreamEncode(paramsList, videoFilter, twoPass),
            audioEncode?.getOutput(videoFile, outputFolder, debugOverlay, thumbnailTime, audioMixPresets)?.audio,
            getOutputFilename(videoFile.file, outputFolder)
        )
    }

    private fun videoFilter(debugOverlay: Boolean): String? {
        val videoFilters = mutableListOf<String>()
        if (width != null && height != null) {
            videoFilters.add("scale=$width:$height:force_original_aspect_ratio=decrease:force_divisible_by=2")
        } else if (width != null || height != null) {
            videoFilters.add("scale=${width ?: -2}:${height ?: -2}")
        }
        filters?.let { videoFilters.addAll(it) }
        if (debugOverlay) {
            videoFilters.add("drawtext=text=$suffix-$codecWithoutPrefix:fontcolor=white:fontsize=50:box=1:boxcolor=black@0.75:boxborderw=5:x=10:y=10")
        }
        return if (videoFilters.isEmpty()) null else videoFilters.joinToString(",")
    }

    private fun getOutputFilename(
        filename: String,
        folder: String
    ) = "$folder/${File(filename).nameWithoutExtension}_${codecWithoutPrefix}_$suffix.$format"
}
