// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.Resource

@ConfigurationProperties("profile")
data class ProfileProperties(
    val location: Resource,
    val spelExpressionPrefix: String = "#{",
    val spelExpressionSuffix: String = "}",
)
