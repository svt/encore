// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import java.util.UUID
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class UUIDToByteArrayConverter : Converter<UUID, ByteArray> {
    override fun convert(source: UUID): ByteArray? {
        return source.toString().toByteArray()
    }
}

@ReadingConverter
class ByteArrayToUUIDConverter : Converter<ByteArray, UUID> {
    override fun convert(source: ByteArray): UUID? {
        return UUID.fromString(String(source))
    }
}
