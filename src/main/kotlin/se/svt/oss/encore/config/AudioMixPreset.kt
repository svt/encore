// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.config

import se.svt.oss.encore.model.profile.ChannelLayout

data class AudioMixPreset(
    val fallbackToAuto: Boolean = true,
    val defaultPan: Map<ChannelLayout, String> = emptyMap(),
    val panMapping: Map<ChannelLayout, Map<ChannelLayout, String>> = emptyMap(),
)
