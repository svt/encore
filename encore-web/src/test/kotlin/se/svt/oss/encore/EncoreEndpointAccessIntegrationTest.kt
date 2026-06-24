// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.reactive.server.WebTestClient
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.AudioVideoInput

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["encore-settings.security.enabled=true"],
)
@ActiveProfiles("test")
@ExtendWith(RedisExtension::class)
class EncoreEndpointAccessIntegrationTest {

    lateinit var webTestClient: WebTestClient

    @LocalServerPort
    var localPort = 0

    @BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$localPort").build()
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
                .bodyValue(
                    EncoreJob(
                        baseName = "TEST",
                        profile = "program",
                        outputFolder = "/test",
                        inputs = listOf(
                            AudioVideoInput(uri = "/some/file.mp4"),
                        ),
                    ),
                )
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
                .bodyValue(
                    EncoreJob(
                        baseName = "TEST",
                        profile = "program",
                        outputFolder = "/test",
                        inputs = listOf(
                            AudioVideoInput(uri = "/some/file.mp4"),
                        ),
                    ),
                )
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
                .json("""{status: "UP", groups:["liveness","readiness"]}""", JsonCompareMode.STRICT)
        }

        @Test
        fun `Anonymous user is authorized GET health readiness without details`() {
            webTestClient.get()
                .uri("/actuator/health/readiness")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectBody()
                .json("""{status: "UP"}""", JsonCompareMode.STRICT)
        }

        @Test
        fun `Anonymous user is not authorized POST`() {
            webTestClient.post()
                .uri("/encoreJobs")
                .headers {
                    it.contentType = MediaType.APPLICATION_JSON
                }
                .bodyValue(
                    EncoreJob(
                        baseName = "TEST",
                        profile = "program",
                        outputFolder = "/test",
                        inputs = listOf(
                            AudioVideoInput(uri = "/some/file.mp4"),
                        ),
                    ),
                )
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized
        }
    }
}
