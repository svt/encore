// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import se.svt.oss.encore.Assertions.assertThat

class X265EncodeTest : VideoEncodeTest<X265Encode>() {
    override fun createEncode(
        width: Int?,
        height: Int?,
        twoPass: Boolean,
        params: LinkedHashMap<String, String>,
        filters: List<String>,
        audioEncode: AudioEncode?
    ): X265Encode = X265Encode(
        width = width,
        height = height,
        twoPass = twoPass,
        ffmpegParams = params,
        codecParams = linkedMapOf("c" to "d"),
        filters = filters,
        audioEncode = audioEncode,
        suffix = "-x265"
    )

    override fun verifyFirstPassParams(encode: VideoEncode, params: List<String>) {
        if (encode.twoPass) {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .noneSatisfy { assertThat(it).contains("c=d:pass=2:stats=log-x265") }
        } else {
            assertThat(params).isEmpty()
        }
    }

    override fun verifySecondPassParams(encode: VideoEncode, params: List<String>) {
        if (encode.twoPass) {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-x265-params", "c=d:pass=2:stats=log-x265")
        } else {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-x265-params", "c=d")
                .noneSatisfy { assertThat(it).contains("pass=2:stats=log-x265") }
        }
    }
}
