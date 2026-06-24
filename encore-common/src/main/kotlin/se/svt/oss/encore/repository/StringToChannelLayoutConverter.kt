// SPDX-FileCopyrightText: 2022 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.repository

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import se.svt.oss.encore.model.profile.ChannelLayout

@Component
@ConfigurationPropertiesBinding
class StringToChannelLayoutConverter : Converter<String, ChannelLayout> {
    override fun convert(source: String): ChannelLayout =
        ChannelLayout.getByNameOrNull(source)
            ?: throw IllegalArgumentException("$source is not a valid channel layout. Valid values: ${ChannelLayout.entries.map { it.layoutName }}")
}
