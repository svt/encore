// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import se.svt.oss.encore.redis.RedisService

@Component
class RedisIndexInitializer(private val redisService: RedisService) {
    @PostConstruct
    fun init() = redisService.createIndex()
}
