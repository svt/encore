package se.svt.oss.encore.repository

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.stereotype.Component
import se.svt.oss.encore.model.profile.ChannelLayout

@Component
@ConfigurationPropertiesBinding
class StringToChannelLayoutConverter : Converter<String, ChannelLayout> {
    override fun convert(source: String): ChannelLayout =
        ChannelLayout.getByNameOrNull(source)
            ?: throw IllegalArgumentException("$source is not a valid channel layout. Valid values: ${ChannelLayout.values().map { it.layoutName }}")
}

@ReadingConverter
class ByteArrayToChannelLayoutConverter : Converter<ByteArray, ChannelLayout> {
    override fun convert(source: ByteArray): ChannelLayout =
        ChannelLayout.getByNameOrNull(String(source))
            ?: throw IllegalArgumentException("${String(source)} is not a valid channel layout. Valid values: ${ChannelLayout.values().map { it.layoutName }}")
}

@WritingConverter
class ChannelLayoutToByteArrayConverter : Converter<ChannelLayout, ByteArray> {
    override fun convert(source: ChannelLayout): ByteArray =
        source.layoutName.toByteArray()
}
