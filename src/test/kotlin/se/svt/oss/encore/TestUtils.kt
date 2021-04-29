// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.mediaanalyzer.file.VideoFile

fun defaultEncoreJob(priority: Int = 0) =
    EncoreJob(
        filename = "file",
        profile = "animerat",
        outputFolder = "outputDir",
        priority = priority
    )

val defaultVideoFile by lazy {
    ObjectMapper().findAndRegisterModules()
        .readValue<VideoFile>(ClassPathResource("/input/video-file.json").file.readText())
}
