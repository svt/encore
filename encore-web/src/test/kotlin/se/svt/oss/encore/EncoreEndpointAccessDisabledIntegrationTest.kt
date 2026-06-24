// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.AudioVideoInput

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["encore-settings.security.enabled=false"],
)
@ActiveProfiles("test")
@ExtendWith(RedisExtension::class)
class EncoreEndpointAccessDisabledIntegrationTest {

    lateinit var webTestClient: WebTestClient

    @LocalServerPort
    var localPort = 0

    @BeforeEach
    fun setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$localPort").build()
    }

    @Test
    fun `Anonymous user can create a job when security is disabled`() {
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
            .isCreated
    }

    @Test
    fun `Anonymous user can get jobs when security is disabled`() {
        webTestClient.get()
            .uri("/encoreJobs")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is2xxSuccessful
    }
}
