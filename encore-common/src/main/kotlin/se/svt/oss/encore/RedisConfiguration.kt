// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import org.redisson.codec.JsonJacksonCodec
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisKeyValueAdapter
import org.springframework.data.redis.core.convert.RedisCustomConversions
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
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

@Configuration
@RegisterReflectionForBinding(
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
    QueueItem::class
)
@EnableRedisRepositories(enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP)
class RedisConfiguration {

    @Bean
    fun redisCustomConversions() = RedisCustomConversions(
        listOf(
            OffsetDateTimeToByteArrayConverter(),
            ByteArrayToOffsetDateTimeConverter(),
            ChannelLayoutToByteArrayConverter(),
            ByteArrayToChannelLayoutConverter()
        )
    )

    @Bean
    fun redissonCustomizer(objectMapper: ObjectMapper): RedissonAutoConfigurationCustomizer {
        return RedissonAutoConfigurationCustomizer { configuration ->
            configuration.codec = JsonJacksonCodec(objectMapper)
        }
    }
}
