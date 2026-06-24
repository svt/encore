// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.config.ProfileProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.profile.AudioEncode
import se.svt.oss.encore.model.profile.DialogueEnhancement
import se.svt.oss.encore.model.profile.GenericVideoEncode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.yaml.YAMLMapper

class ProfileServiceTest {

    private lateinit var profileService: ProfileService
    private val jsonMapper = JsonMapper.builder()
        .findAndAddModules()
        .build()
    private val yamlMapper = YAMLMapper.builder()
        .findAndAddModules()
        .build()

    @BeforeEach
    internal fun setUp() {
        profileService = ProfileService(
            properties = ProfileProperties(ClassPathResource("profile/profiles.yml")),
            jsonMapper = jsonMapper,
            yamlMapper = yamlMapper,
        )
    }

    @Test
    fun `successfully parses valid yaml profiles`() {
        listOf("program-x265", "program", "program-dialogue-enhance").forEach {
            profileService.getProfile(jobWithProfile(it))
        }
    }

    @Test
    fun `program-dialogue-enhance deserializes each DialogueEnhancement shape`() {
        val profile = profileService.getProfile(jobWithProfile("program-dialogue-enhance"))
        val audioEncodes = profile.encodes.filterIsInstance<AudioEncode>()

        val default = audioEncodes.first { it.suffix == "_STEREO_DE_DEFAULT" }
        assertThat(default.dialogueEnhancement).isInstanceOf(DialogueEnhancement.Native::class.java)
        assertThat(default.dialogueEnhancement.enabled).isTrue()

        val native = audioEncodes.first { it.suffix == "_STEREO_DE_NATIVE" }
        assertThat(native.dialogueEnhancement).isInstanceOf(DialogueEnhancement.Native::class.java)
        val nativeStereo = (native.dialogueEnhancement as DialogueEnhancement.Native).dialogueEnhanceStereo
        assertThat(nativeStereo.voice).isEqualTo(2)

        val dnDefaults = audioEncodes.first { it.suffix == "_STEREO_DE_NEURAL" }
        val dnDefaultsTyped = dnDefaults.dialogueEnhancement as DialogueEnhancement.Dn
        assertThat(dnDefaultsTyped.enabled).isTrue()
        assertThat(dnDefaultsTyped.model).isNull()
        assertThat(dnDefaultsTyped.postFilter).isNull()
        assertThat(dnDefaultsTyped.attenuationLimit).isNull()
        assertThat(dnDefaultsTyped.lookahead).isNull()

        val dnLl = audioEncodes.first { it.suffix == "_STEREO_DE_NEURAL_LL" }
        val dnLlTyped = dnLl.dialogueEnhancement as DialogueEnhancement.Dn
        assertThat(dnLlTyped.model).isEqualTo("/opt/homebrew/share/libdf/DeepFilterNet3_LL.tar.gz")
        assertThat(dnLlTyped.lookahead).isEqualTo(0)
        assertThat(dnLlTyped.postFilter).isFalse()
        assertThat(dnLlTyped.attenuationLimit).isEqualTo(50.0)
        assertThat(dnLlTyped.sidechainCompress.ratio).isEqualTo(6)
        assertThat(dnLlTyped.sidechainCompress.threshold).isEqualTo(0.015)
    }

    @Test
    fun `successully uses profile params`() {
        val profile = profileService.getProfile(
            jobWithProfile("archive").copy(
                profileParams = mapOf("height" to 1080, "suffix" to "test_suffix"),
            ),
        )
        assertThat(profile.encodes).describedAs("encodes").hasSize(1)
        val outputProducer = profile.encodes.first()
        assertThat(outputProducer).isInstanceOf(GenericVideoEncode::class.java)
        assertThat(outputProducer as GenericVideoEncode)
            .hasHeight(1080)
            .hasSuffix("test_suffix")
    }

    @Test
    fun `invalid yaml throws exception`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-invalid")) }
            .hasMessageContaining("JSON property suffix due to missing (therefore NULL) value for creator parameter suffix which is a non-nullable type")
    }

    @Test
    fun `unknown profile throws error`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-non-existing")) }
            .hasMessageContaining("Could not find location for profile test-non-existing! Profiles: {")
    }

    @Test
    fun `unreachable profile throws error`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-invalid-location")) }
            .hasMessageContaining("class path resource [profile/test_profile_invalid_location.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `unreachable profiles throws error`() {
        profileService = ProfileService(ProfileProperties(ClassPathResource("nonexisting.yml")), jsonMapper, yamlMapper)
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-profile")) }
            .hasMessageContaining("class path resource [nonexisting.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `profile value empty throw errors`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("none")) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("Could not find location for profile none! Profiles: {")
    }

    private fun jobWithProfile(profile: String) = defaultEncoreJob().copy(profile = profile)
}
