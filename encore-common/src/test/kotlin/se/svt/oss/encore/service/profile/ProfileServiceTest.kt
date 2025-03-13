// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.config.ProfileProperties
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.model.profile.GenericVideoEncode
import java.io.IOException

class ProfileServiceTest {

    private lateinit var profileService: ProfileService
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @BeforeEach
    internal fun setUp() {
        profileService = ProfileService(ProfileProperties(ClassPathResource("profile/profiles.yml")), objectMapper)
    }

    @Test
    fun `successfully parses valid yaml profiles`() {
        listOf("program-x265", "program").forEach {
            profileService.getProfile(jobWithProfile(it))
        }
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
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseInstanceOf(JsonProcessingException::class.java)
            .hasMessageStartingWith("Error parsing profile test-invalid: Instantiation of [simple type, class se.svt.oss.encore.model.profile.X264Encode] value failed")
    }

    @Test
    fun `unknown profile throws error`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-non-existing")) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Could not find location for profile test-non-existing! Profiles: {")
    }

    @Test
    fun `unreachable profile throws error`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-invalid-location")) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("class path resource [profile/test_profile_invalid_location.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `unreachable profiles throws error`() {
        profileService = ProfileService(ProfileProperties(ClassPathResource("nonexisting.yml")), objectMapper)
        assertThatThrownBy { profileService.getProfile(jobWithProfile("test-profile")) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("class path resource [nonexisting.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `profile value empty throw errrors`() {
        assertThatThrownBy { profileService.getProfile(jobWithProfile("none")) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Could not find location for profile none! Profiles: {")
    }

    private fun jobWithProfile(profile: String) = defaultEncoreJob().copy(profile = profile)
}
