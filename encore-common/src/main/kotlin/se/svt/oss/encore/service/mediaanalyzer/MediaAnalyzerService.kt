// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.mediaanalyzer

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.input.AudioIn
import se.svt.oss.encore.model.input.Input
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.mediafile.selectAudioStream
import se.svt.oss.encore.model.mediafile.selectVideoStream
import se.svt.oss.encore.model.mediafile.trimAudio
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.ffprobe.DisplayMatrix
import se.svt.oss.mediaanalyzer.ffprobe.FfAudioStream
import se.svt.oss.mediaanalyzer.ffprobe.FfVideoStream
import se.svt.oss.mediaanalyzer.ffprobe.ProbeResult
import se.svt.oss.mediaanalyzer.ffprobe.SideData
import se.svt.oss.mediaanalyzer.ffprobe.UnknownSideData
import se.svt.oss.mediaanalyzer.ffprobe.UnknownStream
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import se.svt.oss.mediaanalyzer.mediainfo.AudioTrack
import se.svt.oss.mediaanalyzer.mediainfo.GeneralTrack
import se.svt.oss.mediaanalyzer.mediainfo.ImageTrack
import se.svt.oss.mediaanalyzer.mediainfo.MediaInfo
import se.svt.oss.mediaanalyzer.mediainfo.OtherTrack
import se.svt.oss.mediaanalyzer.mediainfo.TextTrack
import se.svt.oss.mediaanalyzer.mediainfo.VideoTrack

private val log = KotlinLogging.logger {}

@Service
@RegisterReflectionForBinding(
    MediaInfo::class,
    AudioTrack::class,
    GeneralTrack::class,
    ImageTrack::class,
    OtherTrack::class,
    TextTrack::class,
    VideoTrack::class,
    ProbeResult::class,
    FfAudioStream::class,
    FfVideoStream::class,
    UnknownStream::class,
    SideData::class,
    DisplayMatrix::class,
    UnknownSideData::class,
)
class MediaAnalyzerService(private val mediaAnalyzer: MediaAnalyzer) {

    fun analyzeInput(input: Input) {
        log.debug { "Analyzing input $input" }
        val probeInterlaced = input is VideoIn && input.probeInterlaced
        val useFirstAudioStreams = (input as? AudioIn)?.channelLayout?.channels?.size

        input.analyzed = mediaAnalyzer.analyze(
            file = input.uri,
            probeInterlaced = probeInterlaced,
            ffprobeInputParams = input.params,
        ).let {
            val selectedVideoStream = (input as? VideoIn)?.videoStream
            val selectedAudioStream = (input as? AudioIn)?.audioStream
            when (it) {
                is VideoFile -> it.selectVideoStream(selectedVideoStream)
                    .selectAudioStream(selectedAudioStream)
                    .trimAudio(useFirstAudioStreams)
                is AudioFile -> it.selectAudioStream(selectedAudioStream)
                    .trimAudio(useFirstAudioStreams)
                else -> it
            }
        }
    }
}
