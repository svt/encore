// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.config.EncoreProperties

@Configuration
class OpenAPIConfiguration {

    @Bean
    fun customOpenAPI(encoreProperties: EncoreProperties): OpenAPI {
        return OpenAPI().info(
            Info()
                .title(encoreProperties.openApi.title)
                .description(encoreProperties.openApi.description)
                .contact(
                    Contact().name(encoreProperties.openApi.contactName)
                        .url(encoreProperties.openApi.contactUrl)
                        .email(encoreProperties.openApi.contactEmail)
                ).license(
                    License().name("EUPL-1.2-or-later")
                        .url("https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12")
                )
        )
    }
}
