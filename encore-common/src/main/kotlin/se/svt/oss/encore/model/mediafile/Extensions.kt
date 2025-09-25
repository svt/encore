// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.mediafile

import io.github.oshai.kotlinlogging.KotlinLogging
import se.svt.oss.encore.model.input.AudioIn
import se.svt.oss.encore.model.profile.ChannelLayout
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.MediaContainer
import se.svt.oss.mediaanalyzer.file.VideoFile

private val log = KotlinLogging.logger { }

fun MediaContainer.audioLayout() = when {
    audioStreams.isEmpty() -> AudioLayout.NONE
    audioStreams.size == 1 -> AudioLayout.MULTI_TRACK
    audioStreams.all { it.channels == 1 } -> AudioLayout.MONO_STREAMS
    audioStreams.first().channels > 1 -> AudioLayout.MULTI_TRACK
    else -> AudioLayout.MIXED_MONO_MULTI
}

fun MediaContainer.channelCount() = when (audioLayout()) {
    AudioLayout.MULTI_TRACK -> audioStreams.first().channels
    AudioLayout.MONO_STREAMS -> audioStreams.size
    // Return number of mono tracks before first multitrack stream,
    // effectively ignoring all other streams
    AudioLayout.MIXED_MONO_MULTI -> audioStreams.indexOfFirst { it.channels > 1 }
    AudioLayout.NONE -> 0
}

fun AudioIn.channelLayout(defaultChannelLayouts: Map<Int, ChannelLayout>): ChannelLayout = when (analyzedAudio.audioLayout()) {
    AudioLayout.NONE -> null
    AudioLayout.MONO_STREAMS, AudioLayout.MIXED_MONO_MULTI -> if (analyzedAudio.channelCount() == channelLayout?.channels?.size) {
        channelLayout
    } else {
        defaultChannelLayouts[analyzedAudio.channelCount()]
            ?: ChannelLayout.defaultChannelLayout(analyzedAudio.channelCount())
    }

    AudioLayout.MULTI_TRACK -> analyzedAudio.audioStreams.first().channelLayout
        ?.let { ChannelLayout.getByNameOrNull(it) }
        ?: defaultChannelLayouts[analyzedAudio.channelCount()]
        ?: ChannelLayout.defaultChannelLayout(analyzedAudio.channelCount())
} ?: throw RuntimeException("Could not determine channel layout for audio input '$audioLabel'!")

fun VideoFile.trimAudio(keep: Int?): VideoFile = if (keep != null && keep < audioStreams.size) {
    log.debug { "Using first $keep audio streams of ${audioStreams.size} of ${this.file}" }
    copy(audioStreams = audioStreams.take(keep))
} else {
    this
}

fun AudioFile.trimAudio(keep: Int?): AudioFile = if (keep != null && keep < audioStreams.size) {
    log.debug { "Using first $keep audio streams of ${audioStreams.size} of ${this.file}" }
    copy(audioStreams = audioStreams.take(keep))
} else {
    this
}

fun VideoFile.selectVideoStream(index: Int?): VideoFile = index?.let {
    copy(videoStreams = listOf(videoStreams[it]))
} ?: this

fun VideoFile.selectAudioStream(index: Int?): VideoFile = index?.let {
    copy(audioStreams = listOf(audioStreams[it]))
} ?: this

fun AudioFile.selectAudioStream(index: Int?): AudioFile = index?.let {
    copy(audioStreams = listOf(audioStreams[it]))
} ?: this

fun Map<String, Any?>.toParams(): List<String> =
    flatMap { entry ->
        listOfNotNull("-${entry.key}", entry.value?.let { "$it" })
            .filterNot { it.isEmpty() }
    }
