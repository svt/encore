// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import mu.KotlinLogging
import org.apache.commons.math3.fraction.Fraction
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.AudioIn
import se.svt.oss.encore.model.input.Input
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.input.inputParams
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.mediafile.audioLayout
import se.svt.oss.encore.model.mediafile.channelCount
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.VideoFile
import se.svt.oss.mediaanalyzer.file.stringValue
import se.svt.oss.mediaanalyzer.file.toFraction
import se.svt.oss.mediaanalyzer.file.toFractionOrNull
import java.io.File

private val defaultAspectRatio = Fraction(16, 9)

class CommandBuilder(
    private val encoreJob: EncoreJob,
    private val profile: Profile,
    private val outputFolder: String
) {
    private val log = KotlinLogging.logger { }

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
        val inputs = encoreJob.inputs.filterIsInstance<VideoIn>()
            .filter { input ->
                outputs.any { it.video?.inputLabels?.contains(input.videoLabel) == true }
            }
        val videoFilters = videoFilters(inputs, outputs)
        val outputParams = outputs.flatMap(this::firstPassParams)
        return inputParams(inputs) + filterParam(videoFilters) + outputParams
    }

    private fun secondPassCommand(outputs: List<Output>): List<String> {
        val videoFilters = videoFilters(encoreJob.inputs, outputs)
        val audioFilters = audioFilters(outputs)
        val outputParams = outputs.flatMap(this::secondPassParams)
        return inputParams(encoreJob.inputs) + filterParam(videoFilters + audioFilters) + outputParams
    }

    private fun audioFilters(outputs: List<Output>): List<String> {
        val audioSplits = encoreJob.inputs.mapIndexedNotNull { index, input ->
            if (input !is AudioIn) return@mapIndexedNotNull null
            val analyzed = input.analyzedAudio
            val splits = outputs.filter { it.audio?.inputLabels?.contains(input.audioLabel) == true }
                .map { output ->
                    output.audio?.filter?.let {
                        MapName.AUDIO.preFilterLabel(input.audioLabel, output.id)
                    }
                        ?: MapName.AUDIO.mapLabel(output.id)
                }
            if (splits.isEmpty()) {
                log.debug { "No audio outputs for audio input ${input.audioLabel}" }
                return@mapIndexedNotNull null
            }
            val split = "asplit=${splits.size}${splits.joinToString("")}"
            val globalAudioFilters = globalAudioFilters(input, analyzed)
            val selector = input.audioStream?.let { "[$index:a:$it]" }
                ?: if (analyzed.audioLayout() == AudioLayout.MONO_STREAMS) "[$index:a]" else "[$index:a:0]"
            val filters = (globalAudioFilters + split).joinToString(",")
            "$selector$filters"
        }
        val streamFilters = outputs.filter { it.audio?.filter != null }
            .mapNotNull { output ->
                output.audio?.let { audioStreamEncode ->
                    val prelabels = audioStreamEncode.inputLabels.map {
                        MapName.AUDIO.preFilterLabel(it, output.id)
                    }.filterNot { audioStreamEncode.filter?.contains(it) == true }
                    "${prelabels.joinToString("")}${audioStreamEncode.filter ?: ""}${MapName.AUDIO.mapLabel(output.id)}"
                }
            }
        return audioSplits + streamFilters
    }

    private fun videoFilters(inputs: List<Input>, outputs: List<Output>): List<String> {
        val videoSplits = inputs.mapIndexedNotNull { index, input ->
            if (input !is VideoIn) return@mapIndexedNotNull null
            val analyzed = input.analyzedVideo
            val splits = outputs.filter { it.video?.inputLabels?.contains(input.videoLabel) == true }
                .map { output ->
                    output.video?.filter?.let {
                        MapName.VIDEO.preFilterLabel(input.videoLabel, output.id)
                    }
                        ?: MapName.VIDEO.mapLabel(output.id)
                }
            if (splits.isEmpty()) {
                log.debug { "No video outputs for video input ${input.videoLabel}" }
                return@mapIndexedNotNull null
            }
            val split = "split=${splits.size}${splits.joinToString("")}"
            val globalVideoFilters = globalVideoFilters(input, analyzed)
            val filters = (globalVideoFilters + split).joinToString(",")
            "[$index:v${input.videoStream?.let { ":$it" } ?: ""}]$filters"
        }
        val streamFilters = outputs.filter { it.video?.filter != null }
            .mapNotNull { output ->
                output.video?.let { videoStreamEncode ->
                    val prelabels = videoStreamEncode.inputLabels.map {
                        MapName.VIDEO.preFilterLabel(it, output.id)
                    }.filterNot { videoStreamEncode.filter?.contains(it) == true }
                    "${prelabels.joinToString("")}${videoStreamEncode.filter ?: ""}${MapName.VIDEO.mapLabel(output.id)}"
                }
            }
        return videoSplits + streamFilters
    }

    private fun filterParam(filters: List<String>): List<String> {
        return listOf("-filter_complex", (listOf("sws_flags=${profile.scaling}") + filters).joinToString(";"))
    }

    private fun inputParams(inputs: List<Input>): List<String> {
        val readDuration = encoreJob.duration?.let {
            it + (encoreJob.seekTo ?: 0.0)
        }
        return listOf(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "+level",
            "-y"
        ) + inputs.inputParams(readDuration)
    }

    private fun globalVideoFilters(input: VideoIn, videoFile: VideoFile): List<String> {
        val filters = mutableListOf<String>()
        val videoStream = videoFile.highestBitrateVideoStream
        if (videoStream.isInterlaced) {
            log.debug { "Video input ${input.videoLabel} is interlaced. Applying deinterlace filter." }
            filters.add("yadif")
        }
        val isAnamorphic = videoStream.sampleAspectRatio?.toFractionOrNull()
            ?.let { it != Fraction(1, 1) } == true

        if (isAnamorphic) {
            log.debug { "Video input ${input.videoLabel} is anamorphic. Scaling to square pixels." }
            val dar = input.dar?.toFraction()
                ?: videoStream.displayAspectRatio?.toFractionOrNull()
                ?: defaultAspectRatio
            filters.add("setdar=${dar.stringValue()}")
            filters.add("scale=iw*sar:ih")
        } else if (videoStream.sampleAspectRatio?.toFractionOrNull() == null) {
            filters.add("setsar=1/1")
        }

        input.cropTo?.toFraction()?.let {
            filters.add("crop=ih*${it.stringValue()}:ih")
        }
        input.padTo?.toFraction()?.let {
            filters.add("pad=aspect=${it.stringValue()}:x=(ow-iw)/2:y=(oh-ih)/2")
        }
        return filters + input.videoFilters
    }

    private fun globalAudioFilters(input: AudioIn, analyzed: MediaContainer): List<String> {
        return if (analyzed.audioLayout() == AudioLayout.MONO_STREAMS) {
            listOf("amerge=inputs=${analyzed.channelCount()}")
        } else {
            emptyList()
        } + input.audioFilters
    }

    private fun firstPassParams(output: Output): List<String> {
        if (output.video == null) {
            return emptyList()
        }
        return listOf("-map", MapName.VIDEO.mapLabel(output.id)) +
            seekParams(output) +
            "-an" +
            durationParams(output) +
            output.video.firstPassParams +
            listOf("-f", output.format, "/dev/null")
    }

    private fun secondPassParams(output: Output): List<String> {
        val mapV: List<String> =
            output.video?.let { listOf("-map", MapName.VIDEO.mapLabel(output.id)) + seekParams(output) }
                ?: emptyList()
        val mapA: List<String> =
            output.audio?.let { listOf("-map", MapName.AUDIO.mapLabel(output.id)) + seekParams(output) }
                ?: emptyList()

        val maps = mapV + mapA
        if (maps.isEmpty()) {
            throw RuntimeException("Neither video or audio in output: $output")
        }

        val videoParams = output.video?.params ?: listOf("-vn")
        val audioParams = output.audio?.params ?: listOf("-an")
        val metaDataParams = listOf("-metadata", "comment=Transcoded using Encore")

        return maps +
            durationParams(output) +
            videoParams + audioParams +
            metaDataParams +
            File(outputFolder).resolve(output.output).toString()
    }

    private fun seekParams(output: Output): List<String> = if (!output.seekable) emptyList() else
        encoreJob.seekTo?.let { listOf("-ss", "$it") } ?: emptyList()

    private fun durationParams(output: Output): List<String> = if (!output.seekable) emptyList() else
        encoreJob.duration?.let { listOf("-t", "$it") } ?: emptyList()

    private enum class MapName {
        VIDEO,
        AUDIO;

        fun mapLabel(id: String) = "[$this-$id]"
        fun preFilterLabel(label: String, id: String) = "[$this-$label-$id]"
    }
}
