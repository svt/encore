// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

class X264EncodeTest : VideoEncodeTest<X264Encode>() {
    override fun createEncode(
        width: Int?,
        height: Int?,
        twoPass: Boolean,
        params: LinkedHashMap<String, String>,
        filters: List<String>,
        audioEncode: AudioEncode?
    ): X264Encode = X264Encode(
        width = width,
        height = height,
        twoPass = twoPass,
        ffmpegParams = params,
        filters = filters,
        audioEncode = audioEncode,
        suffix = "-x264"
    )
}
