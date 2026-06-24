// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.redis

import org.springframework.stereotype.Component
import se.svt.oss.encore.model.CancelEvent
import tools.jackson.databind.json.JsonMapper
import java.nio.ByteBuffer

@Component
class JsonRedisCancelEventCodec(jsonMapper: JsonMapper) : JsonRedisCodec<CancelEvent>(jsonMapper) {
    override fun decodeValue(bytes: ByteBuffer): CancelEvent? = decode(bytes)
}
