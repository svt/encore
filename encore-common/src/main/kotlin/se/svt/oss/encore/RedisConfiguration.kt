// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.convert.RedisCustomConversions
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.RedisEvent
import se.svt.oss.encore.model.SegmentProgressEvent
import se.svt.oss.encore.model.input.AudioInput
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.VideoInput
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.repository.ByteArrayToChannelLayoutConverter
import se.svt.oss.encore.repository.ByteArrayToOffsetDateTimeConverter
import se.svt.oss.encore.repository.ChannelLayoutToByteArrayConverter
import se.svt.oss.encore.repository.OffsetDateTimeToByteArrayConverter
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.ImageFile
import se.svt.oss.mediaanalyzer.file.SubtitleFile
import se.svt.oss.mediaanalyzer.file.VideoFile

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    classes = [
        EncoreJob::class,
        AudioVideoInput::class,
        VideoInput::class,
        AudioInput::class,
        ImageFile::class,
        VideoFile::class,
        AudioFile::class,
        SubtitleFile::class,
        CancelEvent::class,
        SegmentProgressEvent::class,
        QueueItem::class,
    ],
    classNames = ["kotlin.collections.EmptyMap", "kotlin.collections.EmptyList", "kotlin.collections.EmptySet", "java.lang.Enum.EnumDesc"],
)
@EnableRedisRepositories(
    enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP,
    keyspaceNotificationsConfigParameter = "#{\${redis.keyspace.disable-config-notifications:false} ? '' : 'Ex'}",
)
class RedisConfiguration {

    @Bean
    fun redisCustomConversions() = RedisCustomConversions(
        listOf(
            OffsetDateTimeToByteArrayConverter(),
            ByteArrayToOffsetDateTimeConverter(),
            ChannelLayoutToByteArrayConverter(),
            ByteArrayToChannelLayoutConverter(),
        ),
    )

    @Bean
    fun redisMessageListenerContainer(connectionFactory: RedisConnectionFactory): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        return container
    }

    @Bean
    fun redisEventTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, RedisEvent> {
        val template = RedisTemplate<String, RedisEvent>()
        template.connectionFactory = connectionFactory
        template.keySerializer = RedisSerializer.string()
        template.valueSerializer = Jackson2JsonRedisSerializer(objectMapper, RedisEvent::class.java)
        return template
    }

    @Bean
    fun redisQueueTemplate(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper,
    ): RedisTemplate<String, QueueItem> {
        val template = RedisTemplate<String, QueueItem>()
        template.connectionFactory = connectionFactory
        template.keySerializer = RedisSerializer.string()
        template.valueSerializer = Jackson2JsonRedisSerializer(objectMapper, QueueItem::class.java)
        return template
    }

    @Bean
    fun redisQueueMigrationScript() = RedisScript<Boolean>(ClassPathResource("migrate_queue_script.lua"))
}
