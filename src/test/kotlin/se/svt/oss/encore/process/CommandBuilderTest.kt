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
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.output.AudioStreamEncode
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.output.VideoStreamEncode
import se.svt.oss.encore.model.profile.ThumbnailEncode

internal class CommandBuilderTest {
    val profile: Profile = mockk()
    var videoFile = defaultVideoFile
    var encoreJob = defaultEncoreJob()

    private lateinit var commandBuilder: CommandBuilder

    private val metadataComment = "Transcoded using Encore"

    @BeforeEach
    internal fun setUp() {
        encoreJob.input = videoFile

        commandBuilder = CommandBuilder(encoreJob, profile)

        every { profile.scaling } returns "scaling"
    }

    @Test
    fun `one pass encode`() {
        val buildCommands = commandBuilder.buildCommands(listOf(createOutput(false, null)))

        assertThat(buildCommands).hasSize(1)

        val firstPass = buildCommands.first().joinToString(" ")
        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0];[0:a]amerge=inputs=6,asplit=1[AUDIO0] -map [VIDEO0] -map [AUDIO0] libx264 videoParam audioParam -metadata comment=$metadataComment outputFolder")
    }

    @Test
    fun `two pass encode`() {
        val buildCommands = commandBuilder.buildCommands(listOf(createOutput(true, null)))

        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0] -map [VIDEO0] -an libx264 videoParam -pass 1 -passlogfile out0 -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0];[0:a]amerge=inputs=6,asplit=1[AUDIO0] -map [VIDEO0] -map [AUDIO0] libx264 videoParam -pass 2 -passlogfile out0 audioParam -metadata comment=$metadataComment outputFolder")
    }

    @Test
    fun `two pass encode x265`() {
        val buildCommands = commandBuilder.buildCommands(listOf(createOutput(true, null, "libx265")))

        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0] -map [VIDEO0] -an libx265 videoParam -x265-params pass=1:stats=out0 -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0];[0:a]amerge=inputs=6,asplit=1[AUDIO0] -map [VIDEO0] -map [AUDIO0] libx265 videoParam -x265-params pass=2:stats=out0 audioParam -metadata comment=$metadataComment outputFolder")
    }

    @Test
    fun `two pass encode extra allt`() {
        val thumbnailTime = 20000

        videoFile = videoFile.copy(
            videoStreams = videoFile.videoStreams.map {
                it.copy(
                    isInterlaced = true,
                    width = 720,
                    height = 576,
                    sampleAspectRatio = "4:3",
                    displayAspectRatio = "0/0"
                )
            },
            audioStreams = videoFile.audioStreams.slice(1..4).map { it.copy(channels = 1) }
        )

        encoreJob = encoreJob.copy(
            startTime = 10000,
            endTime = 40000,
            thumbnailTime = thumbnailTime,
            cropTo = "1:1",
            padTo = "16:9",
            dar = "16:9",
            globalVideoFilters = listOf("globalVideoFilter"),
            globalAudioFilters = listOf("globalAudioFilter")
        )

        encoreJob.input = videoFile
        commandBuilder = CommandBuilder(encoreJob, profile)

        val buildCommands = commandBuilder.buildCommands(listOf(createOutput(true, thumbnailTime)))

        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]yadif,setdar=16/9,scale=iw*sar:ih,crop=ih*1/1:ih,pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,globalVideoFilter,split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,500),scale=-2:1080[VIDEO0] -map [VIDEO0] -ss 10.0 -t 30.0 -an libx264 videoParam -pass 1 -passlogfile out0 -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]yadif,setdar=16/9,scale=iw*sar:ih,crop=ih*1/1:ih,pad=aspect=16/9:x=(ow-iw)/2:y=(oh-ih)/2,globalVideoFilter,split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,500),scale=-2:1080[VIDEO0];[0:a]amerge=inputs=4,globalAudioFilter,asplit=1[AUDIO0] -map [VIDEO0] -ss 10.0 -t 30.0 -map [AUDIO0] -ss 10.0 -t 30.0 libx264 videoParam -pass 2 -passlogfile out0 audioParam -metadata comment=$metadataComment outputFolder")
    }

    @Test
    fun `two pass encode multi track`() {
        videoFile = videoFile.copy(
            audioStreams = listOf(videoFile.audioStreams.first().copy(channels = 6))
        )

        encoreJob.input = videoFile
        commandBuilder = CommandBuilder(encoreJob, profile)

        val buildCommands = commandBuilder.buildCommands(listOf(createOutput(true, null)))

        assertThat(buildCommands).hasSize(2)

        val firstPass = buildCommands[0].joinToString(" ")
        val secondPass = buildCommands[1].joinToString(" ")

        assertThat(firstPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0] -map [VIDEO0] -an libx264 videoParam -pass 1 -passlogfile out0 -f mp4 /dev/null")
        assertThat(secondPass).isEqualTo("ffmpeg -hide_banner -loglevel +level -y -i ${defaultVideoFile.file} -filter_complex sws_flags=scaling;[0:v]split=1[VIDEO-pre0];[VIDEO-pre0]select=eq(n\\,62)+eq(n\\,125)+eq(n\\,187),scale=-2:1080[VIDEO0];[0:a:0]asplit=1[AUDIO0] -map [VIDEO0] -map [AUDIO0] libx264 videoParam -pass 2 -passlogfile out0 audioParam -metadata comment=$metadataComment outputFolder")
    }

    private fun createOutput(
        twoPass: Boolean,
        thumbnailTime: Int?,
        codec: String = "libx264"
    ): Output {
        val filter =
            ThumbnailEncode().getOutput(defaultVideoFile, encoreJob.outputFolder, false, thumbnailTime, emptyMap())!!.video!!.filter
        val videoStreamEncode =
            VideoStreamEncode(params = listOf(codec, "videoParam"), filter = filter, twoPass = twoPass)
        val audioStreamEncode = AudioStreamEncode(params = listOf("audioParam"), filter = null)
        return Output(videoStreamEncode, audioStreamEncode, "outputFolder")
    }
}
