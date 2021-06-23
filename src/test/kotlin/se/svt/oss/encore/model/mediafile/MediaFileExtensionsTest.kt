// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.mediafile

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.multipleAudioFile
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.multipleVideoFile

internal class MediaFileExtensionsTest {
    @Test
    @DisplayName("Video file without audio streams has AudioLayout NONE")
    fun testAudioLayoutNone() {
        val videoFile = defaultVideoFile.copy(audioStreams = emptyList())
        assertThat(videoFile.audioLayout()).isEqualTo(AudioLayout.NONE)
    }

    @Test
    @DisplayName("When first audio stream has multiple channels, audiolayout is MULTI_TRACK")
    fun testAudioLayoutMultiTrack() {
        val videoFile = defaultVideoFile.copy(
            audioStreams = listOf(
                defaultVideoFile.audioStreams.first().copy(channels = 2)
            )
        )
        assertThat(videoFile.audioLayout()).isEqualTo(AudioLayout.MULTI_TRACK)
    }

    @Test
    @DisplayName("Audio layout is MONO_STREAMS if all streams have 1 channel")
    fun testAudioLayoutMonoStreams() {
        assertThat(defaultVideoFile.audioLayout()).isEqualTo(AudioLayout.MONO_STREAMS)
    }

    @Test
    @DisplayName("Audio layout is multi track when several streams and first is multi channel")
    fun testAudioLayoutSeveralStreamMultiTrack() {
        val videoFile =
            defaultVideoFile.copy(audioStreams = defaultVideoFile.audioStreams.map { it.copy(channels = 2) })
        assertThat(videoFile.audioLayout()).isEqualTo(AudioLayout.MULTI_TRACK)
    }

    @Test
    @DisplayName("Audio layout is invalid if first stream has one channel and the rest has two channels or more")
    fun testAudioLayoutInvalid() {
        val videoFile =
            defaultVideoFile.copy(
                audioStreams = defaultVideoFile.audioStreams.mapIndexed { index, audioStream ->
                    if (index == 0) audioStream else audioStream.copy(channels = 2)
                }
            )
        assertThat(videoFile.audioLayout()).isEqualTo(AudioLayout.INVALID)
    }

    @Test
    @DisplayName("Channel count for multi track is number of channels in first stream")
    fun testChannelCountMultiTrack() {
        val videoFile =
            defaultVideoFile.copy(audioStreams = defaultVideoFile.audioStreams.map { it.copy(channels = 2) })
        assertThat(videoFile.channelCount()).isEqualTo(2)
    }

    @Test
    @DisplayName("Channel count for mono streams is number of streams")
    fun testChannelCountMonoSteams() {
        assertThat(defaultVideoFile.channelCount()).isEqualTo(8)
    }

    @Test
    @DisplayName("Channel count for no streams is 0")
    fun testChannelCountNoSteams() {
        val videoFile = defaultVideoFile.copy(audioStreams = emptyList())
        assertThat(videoFile.channelCount()).isEqualTo(0)
    }

    @Test
    @DisplayName("Video file is not modified when audio trim config is not applicable")
    fun testTrimAudioNoApplied() {
        assertThat(defaultVideoFile.trimAudio(null)).isSameAs(defaultVideoFile)
    }

    @Test
    @DisplayName("Video file discards audio streams according to config")
    fun testTrimAudio() {
        val trimmed = defaultVideoFile.trimAudio(6)
        assertThat(trimmed).hasOnlyVideoStreams(*defaultVideoFile.videoStreams.toTypedArray())
        assertThat(trimmed.audioStreams).hasSize(6)
    }

    @Test
    @DisplayName("Audio file discards audio streams according to config")
    fun testTrimAudioAudioFile() {
        val trimmed = multipleAudioFile.trimAudio(1)
        assertThat(trimmed)
            .hasOnlyAudioStreams(multipleAudioFile.audioStreams.first())
    }

    @Test
    @DisplayName("Audio file selects audio stream according to config")
    fun testSelectAudioStreamAudioFile() {
        val audio = multipleAudioFile.selectAudioStream(1)
        assertThat(audio).hasOnlyAudioStreams(multipleAudioFile.audioStreams.last())
    }

    @Test
    @DisplayName("Video file selects audio stream according to config")
    fun testSelectAudioStreamVideoFile() {
        val video = defaultVideoFile.selectAudioStream(3)
        assertThat(video).hasOnlyAudioStreams(defaultVideoFile.audioStreams[3])
    }

    @Test
    @DisplayName("Video file selects video stream according to config")
    fun testSelectVideoStreamVideoFile() {
        val video = multipleVideoFile.selectVideoStream(1)
        assertThat(video).hasOnlyVideoStreams(multipleVideoFile.videoStreams.last())
    }

    @Test
    @DisplayName("Video file select video stream null does nothing")
    fun testSelectVideoStreamNullVideoFile() {
        val video = multipleVideoFile.selectVideoStream(null)
        assertThat(video).isSameAs(multipleVideoFile)
    }

    @Test
    @DisplayName("Video file select audio stream null does nothing")
    fun testSelectAudioStreamNullVideoFile() {
        val video = defaultVideoFile.selectAudioStream(null)
        assertThat(video).isSameAs(defaultVideoFile)
    }

    @Test
    @DisplayName("Audio file select audio stream null does nothing")
    fun testSelectAudioStreamNullAudioFile() {
        val video = multipleAudioFile.selectAudioStream(null)
        assertThat(video).isSameAs(multipleAudioFile)
    }
}
