// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

class GenericVideoEncodeTest : VideoEncodeTest<GenericVideoEncode>() {
    override fun createEncode(
        width: Int?,
        height: Int?,
        twoPass: Boolean,
        params: LinkedHashMap<String, String>,
        filters: List<String>,
        audioEncode: AudioEncode?
    ) = GenericVideoEncode(
        width = width,
        height = height,
        twoPass = twoPass,
        params = params,
        filters = filters,
        audioEncode = audioEncode,
        suffix = "-generic",
        codec = "acodec",
        format = "mp4"
    )
}
