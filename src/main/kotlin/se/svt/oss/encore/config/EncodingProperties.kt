package se.svt.oss.encore.config

import se.svt.oss.encore.model.profile.ChannelLayout

data class EncodingProperties(
    val audioMixPresets: Map<String, AudioMixPreset> = mapOf("default" to AudioMixPreset()),
    val defaultChannelLayouts: Map<Int, ChannelLayout> = emptyMap(),
    val flipWidthHeightIfPortrait: Boolean = true
)
