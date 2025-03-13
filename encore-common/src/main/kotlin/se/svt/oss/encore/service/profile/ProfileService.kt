// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.SpelParserConfiguration
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.SimpleEvaluationContext
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.ProfileProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.ChannelLayout
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

private val log = KotlinLogging.logger { }

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
    ThumbnailMapEncode::class,
    ChannelLayout::class,
)
@EnableConfigurationProperties(ProfileProperties::class)
class ProfileService(
    private val properties: ProfileProperties,
    private val objectMapper: ObjectMapper,
) {
    private val yamlMapper: YAMLMapper =
        YAMLMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) as YAMLMapper

    private val spelExpressionParser = SpelExpressionParser(
        SpelParserConfiguration(
            null,
            null,
            false,
            false,
            Int.MAX_VALUE,
            100_000,
        ),
    )

    private val spelEvaluationContext = SimpleEvaluationContext
        .forReadOnlyDataBinding()
        .build()

    private val spelParserContext = TemplateParserContext(
        properties.spelExpressionPrefix,
        properties.spelExpressionSuffix,
    )

    private fun mapper() =
        if (properties.location.filename?.let {
                File(it).extension.lowercase(Locale.getDefault()) in setOf("yml", "yaml")
            } == true
        ) {
            yamlMapper
        } else {
            objectMapper
        }

    fun getProfile(job: EncoreJob): Profile = try {
        log.debug { "Get profile ${job.profile}. Reading profiles from ${properties.location}" }
        val profiles = mapper().readValue<Map<String, String>>(properties.location.inputStream)

        profiles[job.profile]
            ?.let { readProfile(it, job) }
            ?: throw RuntimeException("Could not find location for profile ${job.profile}! Profiles: $profiles")
    } catch (e: JsonProcessingException) {
        throw RuntimeException("Error parsing profile ${job.profile}: ${e.message}", e)
    }

    private fun readProfile(path: String, job: EncoreJob): Profile {
        val profile = properties.location.createRelative(path)
        log.debug { "Reading $profile" }
        val profileContent = profile.inputStream.bufferedReader().use { it.readText() }
        val resolvedProfileContent = spelExpressionParser
            .parseExpression(profileContent, spelParserContext)
            .getValue(spelEvaluationContext, job) as String
        return mapper().readValue(resolvedProfileContent)
    }
}
