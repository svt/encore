// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.redis

import org.springframework.stereotype.Component
import se.svt.oss.encore.model.queue.QueueItem
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer

@Component
class JsonRedisQueueItemCodec(jsonMapper: JsonMapper) : JsonRedisCodec<QueueItem>(jsonMapper) {
    override fun decodeValue(bytes: ByteBuffer): QueueItem? = decode(bytes)
}
