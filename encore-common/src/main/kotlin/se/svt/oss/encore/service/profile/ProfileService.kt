// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.GenericVideoEncode
import se.svt.oss.encore.model.profile.OutputProducer
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.model.profile.SimpleAudioEncode
import se.svt.oss.encore.model.profile.ThumbnailEncode
import se.svt.oss.encore.model.profile.ThumbnailMapEncode
import se.svt.oss.encore.model.profile.X264Encode
import se.svt.oss.encore.model.profile.X265Encode
import java.io.File
import java.util.Locale

@Service
@RegisterReflectionForBinding(
    Profile::class,
    OutputProducer::class,
    AudioEncode::class,
    SimpleAudioEncode::class,
    X264Encode::class,
    X265Encode::class,
    GenericVideoEncode::class,
    ThumbnailEncode::class,
    ThumbnailMapEncode::class
)
class ProfileService(
    @Value("\${profile.location}")
    private val profileLocation: Resource,
    objectMapper: ObjectMapper
) {
    private val log = KotlinLogging.logger { }

    private val mapper =
        if (profileLocation.filename?.let { File(it).extension.lowercase(Locale.getDefault()) in setOf("yml", "yaml") } == true) {
            yamlMapper()
        } else {
            objectMapper
        }

    fun getProfile(name: String): Profile = try {
        log.debug { "Get profile $name. Reading profiles from $profileLocation" }
        val profiles = mapper.readValue<Map<String, String>>(profileLocation.inputStream)

        profiles[name]
            ?.let { readProfile(it) }
            ?: throw RuntimeException("Could not find location for profile $name! Profiles: $profiles")
    } catch (e: JsonProcessingException) {
        throw RuntimeException("Error parsing profile $name: ${e.message}", e)
    }

    private fun readProfile(path: String): Profile {
        val profile = profileLocation.createRelative(path)
        log.debug { "Reading $profile" }
        return mapper.readValue(profile.inputStream)
    }

    private fun yamlMapper() =
        YAMLMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}