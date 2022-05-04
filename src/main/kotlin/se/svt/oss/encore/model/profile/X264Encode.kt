// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonProperty
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL

data class X264Encode(
    override val width: Int?,
    override val height: Int?,
    override val twoPass: Boolean,
    @JsonProperty("params")
    override val ffmpegParams: LinkedHashMap<String, String> = linkedMapOf(),
    @JsonProperty("x264-params")
    override val codecParams: LinkedHashMap<String, String> = linkedMapOf(),
    override val filters: List<String> = emptyList(),
    override val audioEncode: AudioEncode? = null,
    override val audioEncodes: List<AudioEncode> = emptyList(),
    override val suffix: String,
    override val format: String = "mp4",
    override val inputLabel: String = DEFAULT_VIDEO_LABEL
) : X26XEncode() {
    override val codecParamName: String
        get() = "x264-params"
    override val codec: String
        get() = "libx264"
}
