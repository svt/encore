// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.redis

import io.lettuce.core.codec.RedisCodec
import io.netty.buffer.Unpooled
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.nio.ByteBuffer

private val emptyBytes = ByteArray(0)

abstract class JsonRedisCodec<T>(protected val jsonMapper: JsonMapper) : RedisCodec<String, T> {

    override fun decodeKey(bytes: ByteBuffer): String? = Unpooled.wrappedBuffer(bytes).toString(Charsets.UTF_8)

    protected inline fun <reified T> decode(bytes: ByteBuffer): T? {
        val valueAsString = Unpooled.wrappedBuffer(bytes).toString(Charsets.UTF_8)
        return if (valueAsString.isEmpty()) {
            null
        } else {
            jsonMapper.readValue<T>(valueAsString)
        }
    }

    override fun encodeKey(key: String?): ByteBuffer? = key?.let { ByteBuffer.wrap(key.toByteArray()) }
        ?: ByteBuffer.wrap(emptyBytes)

    override fun encodeValue(value: T?): ByteBuffer =
        value?.let { ByteBuffer.wrap(jsonMapper.writeValueAsBytes(value)) }
            ?: ByteBuffer.wrap(emptyBytes)
}
