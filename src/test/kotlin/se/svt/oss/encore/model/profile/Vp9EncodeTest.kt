package se.svt.oss.encore.model.profile

import se.svt.oss.encore.Assertions.assertThat

class Vp9EncodeTest : VideoEncodeTest<Vp9Encode>() {
    override fun createEncode(
        width: Int?,
        height: Int?,
        twoPass: Boolean,
        params: LinkedHashMap<String, String>,
        filters: List<String>,
        audioEncode: AudioEncode?
    ): Vp9Encode = Vp9Encode(
        width = width,
        height = height,
        twoPass = twoPass,
        ffmpegParams = params,
        codecParams = linkedMapOf("c" to "d"),
        filters = filters,
        audioEncode = audioEncode,
        suffix = "-vp9"
    )

    override fun verifyFirstPassParams(encode: VideoEncode, params: List<String>) {
        if (encode.twoPass) {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .noneSatisfy{ assertThat(it).containsSequence("-c", "d", "-pass", "2", "-stats", "log-vp9")}
        } else {
            assertThat(params).isEmpty()
        }
    }

    override fun verifySecondPassParams(encode: VideoEncode, params: List<String>) {
        if(encode.twoPass) {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-c", "d")
                .containsSequence("-pass", "2")
                .containsSequence("-stats","log-vp9")
        } else {
            assertThat(params)
                .containsSequence("-a", "b")
                .containsSequence("-c:v", encode.codec)
                .containsSequence("-c", "d")
                .noneSatisfy{assertThat(it).containsSequence("-pass", "2").containsSequence("-stats", "log-vp9")}
        }
    }
}