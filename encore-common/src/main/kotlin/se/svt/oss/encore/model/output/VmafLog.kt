// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.output

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.Collections

data class VmafLog(
    @JsonProperty("pooled_metrics")
    val pooledMetrics: Map<String, PooledMetric> = Collections.emptyMap(),
)

data class PooledMetric(
    val min: Double,
    val max: Double,
    val mean: Double,
    @JsonProperty("harmonic_mean")
    val harmonicMean: Double,
)
