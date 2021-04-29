// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

@ConfigurationProperties("encore-settings")
@ConstructorBinding
data class EncoreProperties(
    val localTemporaryEncode: Boolean = false,
    val audioMixPresets: Map<String, AudioMixPreset> = mapOf("default" to AudioMixPreset()),
    val concurrency: Int = 2,
    val pollInitialDelay: Duration = Duration.ofSeconds(10),
    val pollDelay: Duration = Duration.ofSeconds(5),
    val redisKeyPrefix: String = "encore",
    val security: Security = Security(),
    val openApi: OpenApi = OpenApi()
) {
    data class Security(
        val enabled: Boolean = false,
        val userPassword: String = "",
        val adminPassword: String = ""
    )

    data class OpenApi(
        val title: String = "Encore OpenAPI",
        val description: String = "Endpoints for Encore",
        val contactName: String = "",
        val contactUrl: String = "",
        val contactEmail: String = ""
    )
}
