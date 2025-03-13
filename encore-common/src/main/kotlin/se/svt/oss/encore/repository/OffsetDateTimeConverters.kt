// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import java.time.OffsetDateTime

@WritingConverter
class OffsetDateTimeToByteArrayConverter : Converter<OffsetDateTime, ByteArray> {
    override fun convert(source: OffsetDateTime): ByteArray? = source.toString().toByteArray()
}

@ReadingConverter
class ByteArrayToOffsetDateTimeConverter : Converter<ByteArray, OffsetDateTime> {
    override fun convert(source: ByteArray): OffsetDateTime? = OffsetDateTime.parse(String(source))
}
