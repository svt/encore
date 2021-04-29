// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.output

import java.io.File

data class Output(
    val video: VideoStreamEncode?,
    val audio: AudioStreamEncode?,
    val output: String,
    val format: String = "mp4",
    val fileFilter: ((File) -> Boolean)? = null
)

interface StreamEncode {
    val params: List<String>
    val filter: String?
    val twoPass: Boolean
}

data class VideoStreamEncode(
    override val params: List<String>,
    override val filter: String? = null,
    override val twoPass: Boolean = false
) : StreamEncode

data class AudioStreamEncode(
    override val params: List<String>,
    override val filter: String? = null
) : StreamEncode {
    override val twoPass: Boolean
        get() = false
}
