// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.readValue

fun defaultEncoreJob(priority: Int = 0) =
    EncoreJob(
        profile = "animerat",
        outputFolder = "/output/path",
        priority = priority,
        baseName = "test",
        inputs = listOf(
            AudioVideoInput(
                uri = "/input/test.mp4",
                analyzed = defaultVideoFile,
            ),
        ),
    )

private val jsonMapper = jsonMapper { findAndAddModules() }

val defaultVideoFile by lazy {
    jsonMapper
        .readValue<VideoFile>(ClassPathResource("/input/video-file.json").file.readText())
}

val portraitVideoFile by lazy {
    jsonMapper
        .readValue<VideoFile>(ClassPathResource("/input/portrait-video-file.json").file.readText())
}

val rotateToPortraitVideoFile by lazy {
    jsonMapper
        .readValue<VideoFile>(ClassPathResource("/input/rotate-to-portrait-video-file.json").file.readText())
}

val longVideoFile by lazy {
    jsonMapper
        .readValue<VideoFile>(ClassPathResource("/input/video-file-long.json").file.readText())
}

val multipleAudioFile by lazy {
    jsonMapper
        .readValue<AudioFile>(ClassPathResource("/input/multiple-audio-file.json").file.readText())
}

val multipleVideoFile by lazy {
    jsonMapper
        .readValue<VideoFile>(ClassPathResource("/input/multiple-video-file.json").file.readText())
}
