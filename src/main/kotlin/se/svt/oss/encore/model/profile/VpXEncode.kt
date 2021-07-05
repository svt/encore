package se.svt.oss.encore.model.profile

abstract class VpXEncode : VideoEncode {
    abstract val ffmpegParams: LinkedHashMap<String, String>

    abstract val codecParams: LinkedHashMap<String, String>
    abstract val codecParamName: String

    override val params: Map<String, String>
        // codec parameters for libvpx are passed as normal ffmpeg arguments
        // unlike x26(4|5), where it's one argument
        // thus, we want really only need to concat the maps
        get() = ffmpegParams + if(codecParams.isNotEmpty()) {
            codecParams
        } else {
            emptyMap()
        }
}