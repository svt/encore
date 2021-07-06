package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonProperty
import se.svt.oss.encore.model.input.DEFAULT_VIDEO_LABEL

data class Vp9Encode (
    override val width: Int?,
    override val height: Int?,
    override val twoPass: Boolean,
    @JsonProperty("params")
    override val ffmpegParams: LinkedHashMap<String, String> = linkedMapOf(),
    @JsonProperty("vp9-params")
    override val codecParams: LinkedHashMap<String, String> = linkedMapOf(),
    override val filters: List<String> = emptyList(),
    override val audioEncode: AudioEncode? = null,
    override val suffix: String,
    override val format: String = "webm",
    override val inputLabel: String = DEFAULT_VIDEO_LABEL
) : VpXEncode() {
    override val codecParamName: String
        get() = "vp9-params"
    override val codec: String
        get() = "libvpx-vp9"

    override fun passParams(pass: Int): Map<String, String> {
        // codec args work differently with libvpx than than x26(4|5)
        // we don't need to concat the codecparams into a single string here
        val modifiedCodecParams = codecParams + mapOf("pass" to pass.toString(), "passlogfile" to "log$suffix")
        return modifiedCodecParams
    }
}