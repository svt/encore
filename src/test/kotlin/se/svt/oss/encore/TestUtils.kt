// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.VideoFile

fun defaultEncoreJob(priority: Int = 0) =
    EncoreJob(
        profile = "animerat",
        outputFolder = "/output/path",
        priority = priority,
        baseName = "test",
        inputs = listOf(
            AudioVideoInput(
                uri = "/input/test.mp4",
                analyzed = defaultVideoFile
            )
        )
    )

val defaultVideoFile by lazy {
    ObjectMapper().findAndRegisterModules()
        .readValue<VideoFile>(ClassPathResource("/input/video-file.json").file.readText())
}

val multipleAudioFile by lazy {
    ObjectMapper().findAndRegisterModules()
        .readValue<AudioFile>(ClassPathResource("/input/multiple-audio-file.json").file.readText())
}

val multipleVideoFile by lazy {
    ObjectMapper().findAndRegisterModules()
        .readValue<VideoFile>(ClassPathResource("/input/multiple-video-file.json").file.readText())
}
