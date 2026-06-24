// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore

import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.config.EncoreProperties

@Configuration(proxyBeanMethods = false)
class OpenAPIConfiguration {

    @Bean
    fun openApiCustomizer(
        encoreProperties: EncoreProperties,
        buildProperties: BuildProperties?,
    ): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        val info = openApi.info
        info.title = encoreProperties.openApi.title
        info.version = buildProperties?.version ?: "dev"
        info.description = encoreProperties.openApi.description
        info.contact = Contact().name(encoreProperties.openApi.contactName)
            .url(encoreProperties.openApi.contactUrl)
            .email(encoreProperties.openApi.contactEmail)
        info.license = License().name("EUPL-1.2-or-later")
            .url("https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12")

        openApi.paths.values.forEach { pathItem ->
            pathItem.readOperations().forEach { operation ->
                operation.parameters
                    ?.filter { it.`in` == "query" && it.name == "sort" }
                    ?.forEach { param ->
                        param.description = """
                            Sorting criteria in the format: `property,(asc|desc)`, for example: `sort=createdDate,desc`.
                            
                            Constraints:
                            - Allowed properties: ${listOf("createdDate", "priority").joinToString(", ")}
                            - Only a single sort property is supported per request
                            
                            If an unsupported property is used, the API responds with HTTP 400 and a ProblemDetail body.
                        """.trimIndent()
                    }
            }
        }

        if (encoreProperties.security.enabled) {
            openApi.components
                .addSecuritySchemes(
                    "basicAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic"),
                )
            openApi.addSecurityItem(SecurityRequirement().addList("basicAuth"))
        }
    }
}
