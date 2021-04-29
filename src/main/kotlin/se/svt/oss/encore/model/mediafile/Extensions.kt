// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.mediafile

import mu.KotlinLogging
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.VideoFile

private val log = KotlinLogging.logger { }

// TODO: Some are never used.
fun MediaContainer.audioLayout() = when {
    audioStreams.isEmpty() -> AudioLayout.NONE
    audioStreams.size == 1 -> AudioLayout.MULTI_TRACK
    audioStreams.all { it.channels == 1 } -> AudioLayout.MONO_STREAMS
    audioStreams.first().channels > 1 -> AudioLayout.MULTI_TRACK
    else -> AudioLayout.INVALID
}

fun MediaContainer.channelCount() = if (audioLayout() == AudioLayout.MULTI_TRACK)
    audioStreams.first().channels
else
    audioStreams.sumBy { it.channels }

fun VideoFile.trimAudio(keep: Int?): VideoFile {
    return if (keep != null && keep < audioStreams.size) {
        log.debug { "Using first $keep audio streams of ${audioStreams.size}" }
        copy(audioStreams = audioStreams.take(keep))
    } else this
}
