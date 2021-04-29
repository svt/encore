// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import mu.KotlinLogging
import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.mediafile.audioLayout
import se.svt.oss.encore.model.mediafile.channelCount
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.StreamEncode
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.mediaanalyzer.file.stringValue
import se.svt.oss.mediaanalyzer.file.toFraction
import se.svt.oss.mediaanalyzer.file.toFractionOrNull
import kotlin.math.min

private val commonAspectRatios = setOf(
    Fraction(16, 9),
    Fraction(4, 3),
    Fraction(9, 16),
    Fraction(1, 1),
)
private val defaultAspectRatio = Fraction(16, 9)

class CommandBuilder(
    private val encoreJob: EncoreJob,
    private val profile: Profile
) {
    private val log = KotlinLogging.logger { }
    private val input = encoreJob.inputOrThrow
    private val videoStream = input.highestBitrateVideoStream
    private val dar =
        encoreJob.dar?.toFraction() ?: videoStream.displayAspectRatio?.toFractionOrNull() ?: defaultAspectRatio

    fun buildCommands(outputs: List<Output>): List<List<String>> {
        val (twoPassOuts, singlePassOuts) = outputs.partition { it.video?.twoPass == true }
        return if (twoPassOuts.isNotEmpty()) {
            listOf(
                firstPassCommand(twoPassOuts),
                secondPassCommand(twoPassOuts + singlePassOuts)
            )
        } else {
            listOf(secondPassCommand(singlePassOuts))
        }
    }

    private fun firstPassCommand(
        outputs: List<Output>
    ): List<String> {
        val videoFilters = streamFilters(outputs.map { it.video }, StreamType.VIDEO, globalVideoFilters())
        val outputParams = outputs.mapIndexed(this::firstPassParams).flatten()
        return inputParams() + filterParam(videoFilters) + outputParams
    }

    private fun secondPassCommand(outputs: List<Output>): List<String> {
        val videoFilters = streamFilters(outputs.map { it.video }, StreamType.VIDEO, globalVideoFilters())
        val audioFilters = streamFilters(outputs.map { it.audio }, audioStreamType(), globalAudioFilters())
        val outputParams = outputs.mapIndexed(this::secondPassParams).flatten()
        return inputParams() + filterParam(videoFilters + audioFilters) + outputParams
    }

    private fun filterParam(filters: List<String>): List<String> {
        return listOf("-filter_complex", (listOf("sws_flags=${profile.scaling}") + filters).joinToString(";"))
    }

    private fun audioStreamType() =
        if (input.audioLayout() == AudioLayout.MULTI_TRACK)
            StreamType.MULTI_TRACK_AUDIO
        else
            StreamType.MONO_STREAMS_AUDIO

    private fun inputParams(): List<String> {
        val inputParams = listOf(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "+level",
            "-y",
            "-i",
            input.file
        )

        return inputParams
    }

    private fun highlightSeekParams(): List<String> {
        val params = mutableListOf<String>()
        val startTime = encoreJob.startTime
        if (startTime != null) {
            params += listOf("-ss", ffmpegTimestamp(startTime))
            val endTime = encoreJob.endTime
            if (endTime != null) {
                val durationInMillis = endTime - startTime
                params += listOf("-t", ffmpegTimestamp(durationInMillis))
            }
            return params
        }
        return emptyList()
    }

    private fun globalVideoFilters(): List<String> {
        val filters = mutableListOf<String>()

        if (videoStream.isInterlaced) {
            filters.add("yadif")
        }

        if (isAnamorphic()) {
            filters.add("setdar=${dar.stringValue()}")
            filters.add("scale=iw*sar:ih")
        } else if (videoStream.sampleAspectRatio?.toFractionOrNull() == null) {
            filters.add("setsar=1/1")
        }

        encoreJob.cropTo?.toFraction()?.let {
            filters.add("crop=ih*${it.stringValue()}:ih")
        }
        encoreJob.padTo?.toFraction()?.let {
            filters.add("pad=aspect=${it.stringValue()}:x=(ow-iw)/2:y=(oh-ih)/2")
        }
        return filters + encoreJob.globalVideoFilters
    }

    private fun isAnamorphic(): Boolean {
        val sar = videoStream.sampleAspectRatio
        val preferredCheck = sar?.toFractionOrNull()?.let { it != Fraction(1, 1) } == true
        val par = Fraction(videoStream.width, videoStream.height)
        val maybeAnamorphic = par !in commonAspectRatios
        log.debug { "Anamorphic: $maybeAnamorphic ${videoStream.width}x${videoStream.height} SAR:$sar PAR:${par.stringValue(":")} DAR:${videoStream.displayAspectRatio}" }
        if (maybeAnamorphic != preferredCheck) {
            log.debug { "Anamorphic: checks differ! old method: $maybeAnamorphic, preferred method: $preferredCheck" }
        }
        return preferredCheck
    }

    private fun globalAudioFilters(): List<String> {
        return if (input.audioLayout() == AudioLayout.MONO_STREAMS) {
            listOf("amerge=inputs=${min(input.channelCount(), 6)}")
        } else {
            emptyList()
        } + encoreJob.globalAudioFilters
    }

    private fun streamFilters(
        streamEncodes: List<StreamEncode?>,
        streamType: StreamType,
        globalFilters: List<String>
    ): List<String> {
        val split = streamEncodes
            .mapIndexedNotNull { index, streamEncode ->
                if (streamEncode == null) {
                    null
                } else {
                    streamEncode.filter
                        ?.let { streamType.mapName.preFilterLabel(index) }
                        ?: streamType.mapName.mapLabel(index)
                }
            }
        val filters = streamEncodes
            .mapIndexedNotNull { index, streamEncode ->
                streamEncode?.filter?.let {
                    "${streamType.mapName.preFilterLabel(index)}$it${streamType.mapName.mapLabel(index)}"
                }
            }
        return if (split.isEmpty()) {
            emptyList()
        } else {
            val initialFilters = initialFilter(globalFilters, streamType, split)
            withStreamSelector(streamType, initialFilters, split) + filters
        }
    }

    private fun initialFilter(
        globalFilters: List<String>,
        streamType: StreamType,
        split: List<String>
    ): String {
        val initialFilter = globalFilters + listOf("${streamType.split}=${split.size}")
        return initialFilter.joinToString(",")
    }

    private fun withStreamSelector(
        streamType: StreamType,
        initialFilter: String,
        split: List<String>
    ): List<String> =
        listOf("${streamType.selector}${initialFilter}${split.joinToString("")}")

    private fun firstPassParams(index: Int, output: Output): List<String> {
        if (output.video == null) {
            return emptyList()
        }
        return listOf("-map", MapName.VIDEO.mapLabel(index)) +
            highlightSeekParams() +
            listOf("-an") +
            appendPassParams(1, index, output.video.params) +
            listOf("-f", output.format, "/dev/null")
    }

    private fun secondPassParams(index: Int, output: Output): List<String> {
        val mapV: List<String> =
            output.video?.let { listOf("-map", MapName.VIDEO.mapLabel(index)) + highlightSeekParams() }
                ?: emptyList()
        val mapA: List<String> =
            output.audio?.let { listOf("-map", MapName.AUDIO.mapLabel(index)) + highlightSeekParams() }
                ?: emptyList()
        val maps = mapV + mapA
        if (maps.isEmpty()) {
            throw RuntimeException("Neither video or audio in output: $output")
        }
        val videoParams = if (output.video?.twoPass == true) {
            appendPassParams(2, index, output.video.params)
        } else {
            output.video?.params ?: listOf("-vn")
        }
        val audioParams = output.audio?.params ?: listOf("-an")
        val metaDataParams = listOf("-metadata", "comment=Transcoded using Encore")
        return maps + videoParams + audioParams + metaDataParams + output.output
    }

    private fun appendPassParams(pass: Int, index: Int, videoParams: List<String>): List<String> {
        val modifiedParams = videoParams.toMutableList()
        if (videoParams.contains("libx265")) {
            val indexOfParams = videoParams.indexOf("-x265-params") + 1
            if (indexOfParams > 0) {
                modifiedParams[indexOfParams] += ":pass=$pass:stats=out$index"
            } else {
                modifiedParams.add("-x265-params")
                modifiedParams.add("pass=$pass:stats=out$index")
            }
        } else {
            modifiedParams += listOf("-pass", "$pass", "-passlogfile", "out$index")
        }
        return modifiedParams.toList()
    }

    private enum class MapName {
        VIDEO,
        AUDIO;

        fun mapLabel(index: Int) = "[$this$index]"
        fun preFilterLabel(index: Int) = "[$this-pre$index]"
    }

    private enum class StreamType(val selector: String, val split: String, val mapName: MapName) {
        VIDEO("[0:v]", "split", MapName.VIDEO),
        MONO_STREAMS_AUDIO("[0:a]", "asplit", MapName.AUDIO),
        MULTI_TRACK_AUDIO("[0:a:0]", "asplit", MapName.AUDIO)
    }

    private fun ffmpegTimestamp(durationInMillis: Int): String {
        val millis: Int = (durationInMillis % 1000)
        val second: Int = (durationInMillis / 1000)
        return String.format("%d.%d", second, millis)
    }
}
