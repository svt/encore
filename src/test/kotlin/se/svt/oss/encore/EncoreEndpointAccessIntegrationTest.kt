// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import feign.FeignException
import feign.RequestInterceptor
import feign.auth.BasicAuthRequestInterceptor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.EncoreProperties
import java.io.File

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = ["encore-settings.security.enabled=true", "encore-settings.security.user-password=upw", "encore-settings.security.admin-password=apw"]
)
@ActiveProfiles("test")
class EncoreEndpointAccessIntegrationTest : EncoreIntegrationTestBase() {

    @Test
    fun `security configuration is not loaded in context when security disabled`() {
        val contextRunner = ApplicationContextRunner()
        contextRunner
            .withBean(EncoreProperties::class.java)
            .withPropertyValues("encore-settings.security.enabled=false")
            .withUserConfiguration(SecurityConfiguration::class.java)
            .run { context: AssertableApplicationContext ->
                assertThrows<NoSuchBeanDefinitionException> {
                    context.getBean(SecurityConfiguration::class.java)
                }
            }
    }
}

@Import(UserEndpointAccessIntegrationTest.Conf::class)
class UserEndpointAccessIntegrationTest : EncoreEndpointAccessIntegrationTest() {

    @TestConfiguration
    class Conf {
        @Bean
        fun basicAuthRequestInterceptor(): RequestInterceptor? {
            return BasicAuthRequestInterceptor("user", "upw")
        }
    }

    @Test
    fun `User user is allowed GET`() {
        encoreClient.jobs()
    }

    @Test
    fun `User user is Forbidden POST`() {
        assertThrows<FeignException.Forbidden> {
            encoreClient.createJob(job(File("")))
        }
    }
}

@Import(AdminEndpointAccessIntegrationTest.Conf::class)
class AdminEndpointAccessIntegrationTest : EncoreEndpointAccessIntegrationTest() {

    @TestConfiguration
    class Conf {
        @Bean
        fun basicAuthRequestInterceptor(): RequestInterceptor? {
            return BasicAuthRequestInterceptor("admin", "apw")
        }
    }

    @Test
    fun `Admin user is allowed GET`() {
        encoreClient.jobs()
    }

    @Test
    fun `Admin user is allowed POST`() {
        encoreClient.createJob(job(File("")))
    }
}

class NoUserEndpointAccessIntegrationTest : EncoreEndpointAccessIntegrationTest() {

    data class MyHealth(
        val status: String,
        val components: Map<String, Any>?,
        val groups: List<String>?
    )

    @Test
    fun `Anonymous user is not authorized GET`() {
        assertThrows<FeignException.Unauthorized> {
            encoreClient.jobs()
        }
    }

    @Test
    fun `Anonymous user is authorized GET health without details`() {
        val health = objectMapper.readValue(encoreClient.health(), MyHealth::class.java)
        assertThat(health.status).isEqualTo("UP")
        assertThat(health.components).isNull()
    }

    @Test
    fun `Anonymous user is not authorized POST`() {
        assertThrows<FeignException.Unauthorized> {
            encoreClient.createJob(job(File("")))
        }
    }
}
