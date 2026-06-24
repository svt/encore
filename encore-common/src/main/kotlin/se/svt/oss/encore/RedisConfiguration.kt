// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.config.RedisProperties
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.SegmentProgressEvent
import se.svt.oss.encore.model.input.AudioInput
import se.svt.oss.encore.model.input.AudioVideoInput
import se.svt.oss.encore.model.input.VideoInput
import se.svt.oss.encore.model.output.PooledMetric
import se.svt.oss.encore.model.output.VmafLog
import se.svt.oss.encore.model.queue.QueueItem
import se.svt.oss.encore.redis.JsonRedisCancelEventCodec
import se.svt.oss.encore.redis.JsonRedisQueueItemCodec
import se.svt.oss.encore.redis.JsonRedisSegmentProgressEventCodec
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.ImageFile
import se.svt.oss.mediaanalyzer.file.SubtitleFile
import se.svt.oss.mediaanalyzer.file.VideoFile

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RedisProperties::class)
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
        VmafLog::class,
        PooledMetric::class,
    ],
)
class RedisConfiguration {
    @Bean
    fun redisClient(redisProperties: RedisProperties): RedisClient =
        RedisClient.create(redisProperties.uri)

    @Bean
    fun cancelEventsPubSub(
        redisClient: RedisClient,
        codec: JsonRedisCancelEventCodec,
    ): StatefulRedisPubSubConnection<String, CancelEvent> =
        redisClient.connectPubSub(codec)

    @Bean
    fun segmentEventsPubSub(
        redisClient: RedisClient,
        codec: JsonRedisSegmentProgressEventCodec,
    ): StatefulRedisPubSubConnection<String, SegmentProgressEvent> =
        redisClient.connectPubSub(codec)

    @Bean
    fun redisConnection(
        redisClient: RedisClient,
    ): StatefulRedisConnection<String, String> =
        redisClient.connect()

    @Bean
    fun queueRedisConnection(
        redisClient: RedisClient,
        codec: JsonRedisQueueItemCodec,
    ): StatefulRedisConnection<String, QueueItem> = redisClient.connect(codec)
}
