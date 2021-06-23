// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.mediaanalyzer

import mu.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.input.AudioIn
import se.svt.oss.encore.model.input.Input
import se.svt.oss.encore.model.input.VideoIn
import se.svt.oss.encore.model.mediafile.selectAudioStream
import se.svt.oss.encore.model.mediafile.selectVideoStream
import se.svt.oss.encore.model.mediafile.trimAudio
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.VideoFile

@Service
class MediaAnalyzerService(private val mediaAnalyzer: MediaAnalyzer) {

    private val log = KotlinLogging.logger {}

    fun analyzeInput(input: Input) {
        log.debug { "Analyzing input $input" }
        val probeInterlaced = input is VideoIn && input.probeInterlaced
        val useFirstAudioStreams = (input as? AudioIn)?.useFirstAudioStreams
        input.analyzed = mediaAnalyzer.analyze(input.uri, probeInterlaced).let {
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
