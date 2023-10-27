// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.output.Output

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = AudioEncode::class, name = "AudioEncode"),
    JsonSubTypes.Type(value = SimpleAudioEncode::class, name = "SimpleAudioEncode"),
    JsonSubTypes.Type(value = X264Encode::class, name = "X264Encode"),
    JsonSubTypes.Type(value = X265Encode::class, name = "X265Encode"),
    JsonSubTypes.Type(value = GenericVideoEncode::class, name = "VideoEncode"),
    JsonSubTypes.Type(value = ThumbnailEncode::class, name = "ThumbnailEncode"),
    JsonSubTypes.Type(value = ThumbnailMapEncode::class, name = "ThumbnailMapEncode")
)
interface OutputProducer {
    fun getOutput(job: EncoreJob, encodingProperties: EncodingProperties): Output?

    val type: String
}
