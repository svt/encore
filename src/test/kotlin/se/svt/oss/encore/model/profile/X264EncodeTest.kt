// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.Assertions

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
        codecParams = linkedMapOf("c" to "d"),
        filters = filters,
        audioEncode = audioEncode,
        suffix = "-x264"
    )
    override fun verifyFirstPassParams(encode: VideoEncode, params: List<String>) {
        if (encode.twoPass) {
            Assertions.assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .noneSatisfy { Assertions.assertThat(it).contains("c=d:pass=2:stats=log-x264") }
        } else {
            Assertions.assertThat(params).isEmpty()
        }
    }

    override fun verifySecondPassParams(encode: VideoEncode, params: List<String>) {
        if (encode.twoPass) {
            Assertions.assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-x264-params", "c=d:pass=2:stats=log-x264")
        } else {
            Assertions.assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-x264-params", "c=d")
                .noneSatisfy { Assertions.assertThat(it).contains("pass=2:stats=log-x264") }
        }
    }
}
