// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonProperty

data class X265Encode(
    override val width: Int?,
    override val height: Int?,
    override val twoPass: Boolean,
    @JsonProperty("params")
    override val ffmpegParams: LinkedHashMap<String, String>,
    @JsonProperty("x265-params")
    override val codecParams: LinkedHashMap<String, String>,
    override val filters: List<String> = emptyList(),
    override val audioEncode: AudioEncode? = null,
    override val suffix: String,
    override val format: String = "mp4"
) : X26XEncode() {
    override val codecParamName: String
        get() = "x265-params"
    override val codec: String
        get() = "libx265"
}
