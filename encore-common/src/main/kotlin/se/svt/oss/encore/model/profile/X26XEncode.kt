// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

abstract class X26XEncode : VideoEncode() {

    abstract val ffmpegParams: LinkedHashMap<String, String>

    abstract val codecParams: LinkedHashMap<String, String>
    abstract val codecParamName: String

    override val type: String
        get() = this.javaClass.simpleName

    override val params: Map<String, String>
        get() = ffmpegParams + if (codecParams.isNotEmpty()) {
            mapOf(codecParamName to codecParams.map { "${it.key}=${it.value}" }.joinToString(":"))
        } else {
            emptyMap()
        }

    override fun passParams(pass: Int): Map<String, String> {
        val modifiedCodecParams = (codecParams + mapOf("pass" to pass.toString(), "stats" to "log$suffix"))
            .map { "${it.key}=${it.value}" }
            .joinToString(":")
        return mapOf(codecParamName to modifiedCodecParams)
    }
}
