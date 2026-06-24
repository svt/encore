// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.boot.jackson.JacksonComponent
import se.svt.oss.encore.model.Status
import se.svt.oss.encore.model.mediafile.AudioLayout
import se.svt.oss.encore.model.profile.ChannelLayout
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.deser.std.StdDeserializer
import tools.jackson.databind.exc.InvalidFormatException
import tools.jackson.databind.exc.MismatchedInputException
import tools.jackson.databind.ser.std.StdSerializer

@JacksonComponent
class EncoreJacksonComponent {
    class StatusDeserializer : StdDeserializer<Status>(Status::class.java) {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext,
        ): Status = p.valueAsString?.let {
            try {
                Status.valueOf(it)
            } catch (e: IllegalArgumentException) {
                throw InvalidFormatException.from(p, "${e.message}", it, Status::class.java)
            }
        } ?: throw MismatchedInputException.from(p, Status::class.java, "Was expecting a string!")
    }

    class StatusSerializer : StdSerializer<Status>(Status::class.java) {
        override fun serialize(
            value: Status,
            gen: JsonGenerator,
            provider: SerializationContext?,
        ) {
            gen.writeString(value.name)
        }
    }

    class ChannelLayoutDeserializer : StdDeserializer<ChannelLayout>(ChannelLayout::class.java) {
        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext?,
        ): ChannelLayout = p.valueAsString?.let {
            ChannelLayout.getByNameOrNull(it)
                ?: throw InvalidFormatException.from(p, "ChannelLayout $it not found", it, ChannelLayout::class.java)
        } ?: throw MismatchedInputException.from(p, ChannelLayout::class.java, "Was expecting a string!")
    }

    class ChannelLayoutSerializer : StdSerializer<ChannelLayout>(ChannelLayout::class.java) {
        override fun serialize(
            value: ChannelLayout,
            gen: JsonGenerator,
            provider: SerializationContext?,
        ) {
            gen.writeString(value.layoutName)
        }
    }
}
