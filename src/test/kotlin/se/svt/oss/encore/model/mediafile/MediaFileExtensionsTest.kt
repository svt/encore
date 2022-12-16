// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.mediafile

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.input.AudioInput
import se.svt.oss.encore.model.profile.ChannelLayout
import se.svt.oss.encore.multipleAudioFile
import se.svt.oss.encore.multipleVideoFile
import se.svt.oss.mediaanalyzer.file.MediaFile

internal class MediaFileExtensionsTest {
    private val noAudio = defaultVideoFile.copy(audioStreams = emptyList())

    private val invalidAudio = defaultVideoFile.copy(
        audioStreams = defaultVideoFile.audioStreams.mapIndexed { index, audioStream ->
            if (index == 0) audioStream else audioStream.copy(channels = 2)
        }
    )

    private val defaultChannelLayouts = mapOf(3 to ChannelLayout.CH_LAYOUT_3POINT0)

    @Test
    @DisplayName("Video file without audio streams has AudioLayout NONE")
    fun testAudioLayoutNone() {
        assertThat(noAudio.audioLayout()).isEqualTo(AudioLayout.NONE)
    }

    @Test
    @DisplayName("When first audio stream has multiple channels, audiolayout is MULTI_TRACK")
    fun testAudioLayoutMultiTrack() {
        assertThat(multipleAudioFile.audioLayout()).isEqualTo(AudioLayout.MULTI_TRACK)
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
        assertThat(invalidAudio.audioLayout()).isEqualTo(AudioLayout.INVALID)
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
        assertThat(noAudio.channelCount()).isEqualTo(0)
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

    @Test
    @DisplayName("Channel layout throws when no audio")
    fun channelLayoutNoAudio() {
        assertThatThrownBy { audioInput(noAudio).channelLayout(defaultChannelLayouts) }
            .hasMessage("Could not determine channel layout for audio input 'main'!")
    }

    @Test
    @DisplayName("Channel layout throws when invalid audio")
    fun channelLayoutInvalidAudio() {
        assertThatThrownBy { audioInput(invalidAudio).channelLayout(defaultChannelLayouts) }
            .hasMessage("Could not determine channel layout for audio input 'main'!")
    }

    @Test
    @DisplayName("Channel layout is set on input and channel count correct")
    fun channelLayoutMonoStreamsSetByParam() {
        val input = audioInput(defaultVideoFile.copy(audioStreams = defaultVideoFile.audioStreams.take(3)))
            .copy(channelLayout = ChannelLayout.CH_LAYOUT_3POINT0_BACK)
        assertThat(input.channelLayout(defaultChannelLayouts)).isEqualTo(ChannelLayout.CH_LAYOUT_3POINT0_BACK)
    }

    @Test
    @DisplayName("Channel layout is set on input and channel count incorrect use ffmpeg default")
    fun channelLayoutMonoStreamsSetByParamIncorrect() {
        val input = audioInput(defaultVideoFile.copy(audioStreams = defaultVideoFile.audioStreams.take(2)))
            .copy(channelLayout = ChannelLayout.CH_LAYOUT_3POINT0)
        assertThat(input.channelLayout(defaultChannelLayouts)).isEqualTo(ChannelLayout.CH_LAYOUT_STEREO)
    }

    @Test
    @DisplayName("Channel layout is set on input and channel count incorrect use default by config")
    fun channelLayoutMonoStreamsSetByParamIncorrectUseConfig() {
        val input = audioInput(defaultVideoFile.copy(audioStreams = defaultVideoFile.audioStreams.take(3)))
            .copy(channelLayout = ChannelLayout.CH_LAYOUT_5POINT1)
        assertThat(input.channelLayout(defaultChannelLayouts)).isEqualTo(ChannelLayout.CH_LAYOUT_3POINT0)
    }

    @Test
    @DisplayName("Channel layout is present in analyzed")
    fun channelLayoutMultiTrack() {
        val audioInput = audioInput(multipleAudioFile.copy(audioStreams = multipleAudioFile.audioStreams.drop(1)))
        assertThat(audioInput.channelLayout(defaultChannelLayouts))
            .isEqualTo(ChannelLayout.CH_LAYOUT_5POINT1_SIDE)
    }

    @Test
    @DisplayName("Channel layout is not present in analyzed - uses default from config")
    fun channelLayoutMultiTrackNotPresentInAnalyzedUseConfig() {
        val audioFile = multipleAudioFile.copy(
            audioStreams = multipleAudioFile.audioStreams.take(1).map {
                it.copy(channelLayout = null, channels = 3)
            }
        )

        assertThat(audioInput(audioFile).channelLayout(defaultChannelLayouts))
            .isEqualTo(ChannelLayout.CH_LAYOUT_3POINT0)
    }

    @Test
    @DisplayName("Channel layout is not present in analyzed - uses default from ffmpeg")
    fun channelLayoutMultiTrackNotPresentInAnalyzedUseDefault() {
        val audioFile = multipleAudioFile.copy(
            audioStreams = multipleAudioFile.audioStreams.take(1).map {
                it.copy(channelLayout = null, channels = 3)
            }
        )

        assertThat(audioInput(audioFile).channelLayout(emptyMap()))
            .isEqualTo(ChannelLayout.CH_LAYOUT_2POINT1)
    }

    private fun audioInput(analyzed: MediaFile) = AudioInput(
        uri = "/test.mp",
        analyzed = analyzed
    )
}
