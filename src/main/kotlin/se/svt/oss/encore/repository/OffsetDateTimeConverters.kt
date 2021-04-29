// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import java.time.OffsetDateTime
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class OffsetDateTimeToByteArrayConverter : Converter<OffsetDateTime, ByteArray> {
    override fun convert(source: OffsetDateTime): ByteArray? {
        return source.toString().toByteArray()
    }
}

@ReadingConverter
class ByteArrayToOffsetDateTimeConverter : Converter<ByteArray, OffsetDateTime> {
    override fun convert(source: ByteArray): OffsetDateTime? {
        return OffsetDateTime.parse(String(source))
    }
}
