// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.config

data class AudioMixPreset(
    val fallbackToAuto: Boolean = true,
    val defaultPan: Map<Int, String> = emptyMap(),
    val panMapping: Map<Int, Map<Int, String>> = emptyMap()
)
