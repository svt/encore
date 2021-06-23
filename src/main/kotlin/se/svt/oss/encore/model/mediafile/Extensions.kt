// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.mediafile

import mu.KotlinLogging
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.VideoFile

private val log = KotlinLogging.logger { }

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
    audioStreams.sumOf { it.channels }

fun VideoFile.trimAudio(keep: Int?): VideoFile {
    return if (keep != null && keep < audioStreams.size) {
        log.debug { "Using first $keep audio streams of ${audioStreams.size} of ${this.file}" }
        copy(audioStreams = audioStreams.take(keep))
    } else this
}

fun AudioFile.trimAudio(keep: Int?): AudioFile {
    return if (keep != null && keep < audioStreams.size) {
        log.debug { "Using first $keep audio streams of ${audioStreams.size} of ${this.file}" }
        copy(audioStreams = audioStreams.take(keep))
    } else this
}

fun VideoFile.selectVideoStream(index: Int?): VideoFile {
    return index?.let {
        copy(videoStreams = listOf(videoStreams[it]))
    } ?: this
}

fun VideoFile.selectAudioStream(index: Int?): VideoFile {
    return index?.let {
        copy(audioStreams = listOf(audioStreams[it]))
    } ?: this
}

fun AudioFile.selectAudioStream(index: Int?): AudioFile {
    return index?.let {
        copy(audioStreams = listOf(audioStreams[it]))
    } ?: this
}

fun Map<String, Any?>.toParams(): List<String> =
    flatMap { entry ->
        listOfNotNull("-${entry.key}", entry.value?.let { "$it" })
    }
