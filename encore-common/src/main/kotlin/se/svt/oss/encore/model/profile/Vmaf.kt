// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import java.util.Collections

data class Vmaf(
    val enabled: Boolean = false,
    val feature: String? = null,
    val model: String? = null,
    val threads: Int? = null,
    val subsample: Int? = null,
    val refFilters: List<String> = Collections.emptyList(),
)
