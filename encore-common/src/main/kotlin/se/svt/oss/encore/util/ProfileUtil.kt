package se.svt.oss.encore.util

import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.AudioEncoder
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.profile.SimpleAudioEncode
import se.svt.oss.encore.model.profile.VideoEncode

fun Profile.hasAudioEncodes(): Boolean = this.encodes
    .filter { it.enabled }
    .any { encode ->
        when (encode) {
            is VideoEncode ->
                encode.audioEncode?.enabled == true || encode.audioEncodes.any { it.enabled }
            is AudioEncode,
            is SimpleAudioEncode,
            -> true
            else -> false
        }
    }

fun Profile.allAudioEncodes(): List<AudioEncoder> =
    this.encodes
        .filter { it.enabled }
        .flatMap { encode ->
            when (encode) {
                is VideoEncode -> encode.allAudioEncodes()
                is AudioEncoder -> listOf(encode)
                else -> emptyList()
            }
        }

fun VideoEncode.allAudioEncodes(): List<AudioEncoder> {
    val audioEncodes = mutableListOf<AudioEncoder>()
    this.audioEncode?.let {
        if (it.enabled) {
            audioEncodes.add(it)
        }
    }
    audioEncodes.addAll(this.audioEncodes.filter { it.enabled })
    return audioEncodes
}

fun Profile.hasVideoEncodes(): Boolean = this.encodes
    .filter { it.enabled }
    .any { encode ->
        when (encode) {
            is VideoEncode -> true
            else -> false
        }
    }
