// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.Assertions.assertThatThrownBy
import java.io.IOException

class ProfileServiceTest {

    private lateinit var profileService: ProfileService
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @BeforeEach
    internal fun setUp() {
        profileService = ProfileService(ClassPathResource("profile/profiles.yml"), objectMapper)
    }

    @Test
    fun `successfully parses valid yaml profiles`() {
        listOf("archive", "program-x265", "program").forEach {
            profileService.getProfile(it)
        }
    }

    @Test
    fun `invalid yaml throws exception`() {
        assertThatThrownBy { profileService.getProfile("test-invalid") }
            .isInstanceOf(RuntimeException::class.java)
            .hasCauseInstanceOf(JsonProcessingException::class.java)
            .hasMessageStartingWith("Error parsing profile test-invalid: Instantiation of [simple type, class se.svt.oss.encore.model.profile.X264Encode] value failed")
    }

    @Test
    fun `unknown profile throws error`() {
        assertThatThrownBy { profileService.getProfile("test-non-existing") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Could not find location for profile test-non-existing! Profiles: {")
    }

    @Test
    fun `unreachable profile throws error`() {
        assertThatThrownBy { profileService.getProfile("test-invalid-location") }
            .isInstanceOf(IOException::class.java)
            .hasMessage("class path resource [profile/test_profile_invalid_location.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `unreachable profiles throws error`() {
        profileService = ProfileService(ClassPathResource("nonexisting.yml"), objectMapper)
        assertThatThrownBy { profileService.getProfile("test-profile") }
            .isInstanceOf(IOException::class.java)
            .hasMessage("class path resource [nonexisting.yml] cannot be opened because it does not exist")
    }

    @Test
    fun `profile value empty throw errrors`() {
        assertThatThrownBy { profileService.getProfile("none") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageStartingWith("Could not find location for profile none! Profiles: {")
    }
}
