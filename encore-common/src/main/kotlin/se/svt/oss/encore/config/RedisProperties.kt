// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "encore-settings.redis")
data class RedisProperties(
    // See https://redis.github.io/lettuce/user-guide/connecting-redis/#uri-syntax
    val uri: String = "redis://localhost:6379",
    val jobExpireTime: Duration = Duration.ofDays(7),
    val prefix: String = "encore",
)
