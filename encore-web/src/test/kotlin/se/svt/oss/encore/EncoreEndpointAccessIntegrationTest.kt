// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.assertj.AssertableApplicationContext
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.junit5.redis.EmbeddedRedisExtension

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["encore-settings.security.enabled=true"]
)
@ActiveProfiles("test")
@ExtendWith(EmbeddedRedisExtension::class)
class EncoreEndpointAccessIntegrationTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

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

    @Nested
    inner class User {
        @Test
        fun `User user is allowed GET`() {
            webTestClient.get()
                .uri("/encoreJobs")
                .headers { it.setBasicAuth("user", "upw") }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is2xxSuccessful
        }

        @Test
        fun `User user is Forbidden POST`() {
            webTestClient.post()
                .uri("/encoreJobs")
                .headers {
                    it.setBasicAuth("user", "upw")
                    it.contentType = MediaType.APPLICATION_JSON
                }
                .bodyValue(EncoreJob(baseName = "TEST", profile = "program", outputFolder = "/test"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isForbidden
        }
    }

    @Nested
    inner class Admin {
        @Test
        fun `Admin user is allowed GET`() {
            webTestClient.get()
                .uri("/encoreJobs")
                .headers { it.setBasicAuth("admin", "apw") }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is2xxSuccessful
        }

        @Test
        fun `Admin user is allowed POST`() {
            webTestClient.post()
                .uri("/encoreJobs")
                .headers {
                    it.setBasicAuth("admin", "apw")
                    it.contentType = MediaType.APPLICATION_JSON
                }
                .bodyValue(EncoreJob(baseName = "TEST", profile = "program", outputFolder = "/test"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is2xxSuccessful
        }

        @Test
        fun `Admin user is authorized GET health with details`() {
            webTestClient.get()
                .uri("/actuator/health")
                .headers {
                    it.setBasicAuth("admin", "apw")
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().is2xxSuccessful
                .expectBody()
                .jsonPath("\$.status").isEqualTo("UP")
                .jsonPath("\$.components..details").isNotEmpty
        }
    }

    @Nested
    inner class Anonymous {
        @Test
        fun `Anonymous user is not authorized GET`() {
            webTestClient.get()
                .uri("/encoreJobs")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }

        @Test
        fun `Anonymous user is authorized GET health without details`() {
            webTestClient.get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody()
                .json("""{status: "UP", groups:["liveness","readiness"]}""", true)
        }

        @Test
        fun `Anonymous user is authorized GET health readiness without details`() {
            webTestClient.get()
                .uri("/actuator/health/readiness")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody()
                .json("""{status: "UP"}""", true)
        }

        @Test
        fun `Anonymous user is not authorized POST`() {
            webTestClient.post()
                .uri("/encoreJobs")
                .headers {
                    it.contentType = MediaType.APPLICATION_JSON
                }
                .bodyValue(EncoreJob(baseName = "TEST", profile = "program", outputFolder = "/test"))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }
    }
}
