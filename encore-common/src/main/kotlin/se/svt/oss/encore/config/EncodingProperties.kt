// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.config

import org.springframework.boot.context.properties.NestedConfigurationProperty
import se.svt.oss.encore.model.profile.ChannelLayout
import java.util.Collections

data class EncodingProperties(
    @NestedConfigurationProperty
    val audioMixPresets: Map<String, AudioMixPreset> = mapOf("default" to AudioMixPreset()),
    @NestedConfigurationProperty
    val defaultChannelLayouts: Map<Int, ChannelLayout> = Collections.emptyMap(),
    val flipWidthHeightIfPortrait: Boolean = true,
    val exitOnError: Boolean = true,
    val globalParams: LinkedHashMap<String, Any?> = linkedMapOf(),
    val includeQualityMetrics: Set<String> = setOf("vmaf"),
    val filterValidFfprobeParams: Boolean = true,
    val protocolInputParams: Map<String, LinkedHashMap<String, String?>> = mapOf(
        "http" to linkedMapOf("reconnect" to "1", "reconnect_on_network_error" to "1"),
    ),
)
