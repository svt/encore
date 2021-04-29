// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.redisson.api.RedissonClient
import org.redisson.spring.data.connection.RedissonConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.core.convert.RedisCustomConversions
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import se.svt.oss.encore.repository.ByteArrayToOffsetDateTimeConverter
import se.svt.oss.encore.repository.ByteArrayToURIConverter
import se.svt.oss.encore.repository.ByteArrayToUUIDConverter
import se.svt.oss.encore.repository.OffsetDateTimeToByteArrayConverter
import se.svt.oss.encore.repository.URIToByteArrayConverter
import se.svt.oss.encore.repository.UUIDToByteArrayConverter

@Configuration
@EnableRedisRepositories(enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP)
class RedisConfiguration {

    @Bean
    fun redisCustomConversions() = RedisCustomConversions(
        listOf(
            OffsetDateTimeToByteArrayConverter(),
            ByteArrayToOffsetDateTimeConverter(),
            UUIDToByteArrayConverter(),
            ByteArrayToUUIDConverter(),
            URIToByteArrayConverter(),
            ByteArrayToURIConverter()
        )
    )

    @Bean
    fun redissonConnectionFactory(redisson: RedissonClient) = RedissonConnectionFactory(redisson)
}
