// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.model.input.AudioInput
import se.svt.oss.encore.model.input.DEFAULT_AUDIO_LABEL
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL
import se.svt.oss.encore.model.input.VideoInput
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.mediaanalyzer.file.AudioFile

internal class CommandBuilderTest {
    val profile: Profile = mockk()
    var videoFile = defaultVideoFile
    var encoreJob = defaultEncoreJob()

    private lateinit var commandBuilder: CommandBuilder

    private val metadataComment = "Transcoded using Encore"

    @BeforeEach
    internal fun setUp() {
        commandBuilder = CommandBuilder(encoreJob, profile, encoreJob.outputFolder)

        every { profile.scaling } returns "scaling"
        every { profile.deinterlaceFilter } returns "yadif"
    }

    @Test
    fun `one pass encode`() {
        val buildCommands = commandBuilder.buildCommands(listOf(output(false)))

        assertThat(buildCommands).hasSize(1)

        val command = buildCommands.first().joinToString(" ")
        assertThat(command).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i /input/test.mp4 -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-main-test-out];[VIDEO-main-test-out]video-filter[VIDEO-test-out];[0:a]amerge=inputs=8,asplit=1[AUDIO-main-test-out-0];[AUDIO-main-test-out-0]audio-filter[AUDIO-test-out-0] -map [VIDEO-test-out] -map [AUDIO-test-out-0] video params audio params -metadata comment=Transcoded using Encore /output/path/out.mp4")
    }

    @Test
    fun `two pass encode`() {
        val buildCommands = commandBuilder.buildCommands(listOf(output(true)))
        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-main-test-out];[VIDEO-main-test-out]video-filter[VIDEO-test-out] -map [VIDEO-test-out] -an first pass -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-main-test-out];[VIDEO-main-test-out]video-filter[VIDEO-test-out];[0:a]amerge=inputs=8,asplit=1[AUDIO-main-test-out-0];[AUDIO-main-test-out-0]audio-filter[AUDIO-test-out-0] -map [VIDEO-test-out] -map [AUDIO-test-out-0] video params audio params -metadata comment=$metadataComment /output/path/out.mp4")
    }

    @Test
    fun `two pass encode extra`() {
        videoFile = videoFile.copy(
            videoStreams = videoFile.videoStreams.map {
                it.copy(
                    isInterlaced = true,
                    width = 720,
                    height = 576,
                    sampleAspectRatio = "4:3",
                    displayAspectRatio = "0/0"
                )
            }
        )
        val mainAudioFile = AudioFile(
            file = "/input/main-audio.mp4",
            fileSize = 1000,
            format = "AAC",
            overallBitrate = 1000,
            duration = videoFile.duration,
            audioStreams = videoFile.audioStreams.slice(1..4).map { it.copy(channels = 1) }
        )
        val secondaryAudioFile = AudioFile(
            file = "/input/other-audio.mp4",
            fileSize = 1000,
            format = "AAC",
            overallBitrate = 1000,
            duration = videoFile.duration,
            audioStreams = videoFile.audioStreams.take(1).map { it.copy(channels = 6) }
        )

        val inputs = listOf(
            VideoInput(
                uri = videoFile.file,
                params = linkedMapOf("f" to "mp4"),
                dar = "16:9",
                cropTo = "1:1",
                padTo = "16:9",
                videoFilters = listOf("video", "filter"),
                analyzed = videoFile,
                videoStream = 1
            ),
            AudioInput(
                uri = mainAudioFile.file,
                params = linkedMapOf("ac" to "4"),
                audioFilters = listOf("audio-main", "main-filter"),
                analyzed = mainAudioFile,
            ),
            AudioInput(
                uri = secondaryAudioFile.file,
                audioLabel = "other",
                audioStream = 3,
                analyzed = secondaryAudioFile
            )
        )
        encoreJob = encoreJob.copy(
            seekTo = 12.1,
            duration = 10.4,
            inputs = inputs
        )

        commandBuilder = CommandBuilder(encoreJob, profile, "/tmp/123")

        val buildCommands = commandBuilder.buildCommands(listOf(output(true), audioOutput("other", "extra")))

        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -f mp4 -t 22.5 -i /input/test.mp4 -filter_complex sws_flags=scaling;[0:v:1]yadif,setdar=16/9,scale=iw*sar:ih,crop=ih*1/1:ih,pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,video,filter,split=1[VIDEO-main-test-out];[VIDEO-main-test-out]video-filter[VIDEO-test-out] -map [VIDEO-test-out] -ss 12.1 -an -t 10.4 first pass -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -f mp4 -t 22.5 -i /input/test.mp4 -ac 4 -t 22.5 -i /input/main-audio.mp4 -t 22.5 -i /input/other-audio.mp4 -filter_complex sws_flags=scaling;[0:v:1]yadif,setdar=16/9,scale=iw*sar:ih,crop=ih*1/1:ih,pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,video,filter,split=1[VIDEO-main-test-out];[VIDEO-main-test-out]video-filter[VIDEO-test-out];[1:a]amerge=inputs=4,audio-main,main-filter,asplit=1[AUDIO-main-test-out-0];[2:a:3]asplit=1[AUDIO-other-extra-0];[AUDIO-main-test-out-0]audio-filter[AUDIO-test-out-0];[AUDIO-other-extra-0]audio-filter-extra[AUDIO-extra-0] -map [VIDEO-test-out] -ss 12.1 -map [AUDIO-test-out-0] -ss 12.1 -t 10.4 video params audio params -metadata comment=Transcoded using Encore /tmp/123/out.mp4 -map [AUDIO-extra-0] -ss 12.1 -t 10.4 -vn audio extra -metadata comment=Transcoded using Encore /tmp/123/extra.mp4")
    }

    private fun output(twoPass: Boolean): Output {
        val videoStreamEncode = VideoStreamEncode(
            params = listOf("video", "params"),
            firstPassParams = if (twoPass) listOf("first", "pass") else emptyList(),
            filter = "video-filter",
            twoPass = twoPass,
            inputLabels = listOf(DEFAULT_VIDEO_LABEL)
        )
        val audioStreamEncode = AudioStreamEncode(
            params = listOf("audio", "params"),
            filter = "audio-filter",
            inputLabels = listOf(DEFAULT_AUDIO_LABEL)
        )
        return Output(
            video = videoStreamEncode,
            audioStreams = listOf(audioStreamEncode),
            output = "out.mp4",
            id = "test-out"
        )
    }

    fun audioOutput(label: String, id: String): Output {
        return Output(
            id = id,
            output = "$id.mp4",
            video = null,
            audioStreams = listOf(
                AudioStreamEncode(
                    params = listOf("audio", id),
                    filter = "audio-filter-$id",
                    inputLabels = listOf(label)
                )
            )
        )
    }
}
