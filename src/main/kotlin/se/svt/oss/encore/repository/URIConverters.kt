// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import java.net.URI
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class URIToByteArrayConverter : Converter<URI, ByteArray> {
    override fun convert(source: URI): ByteArray? {
        return source.toString().toByteArray()
    }
}

@ReadingConverter
class ByteArrayToURIConverter : Converter<ByteArray, URI> {
    override fun convert(source: ByteArray): URI? {
        return URI.create(String(source))
    }
}
