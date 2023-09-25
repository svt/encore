// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Configuration
import org.springframework.data.rest.core.config.RepositoryRestConfiguration
import org.springframework.data.rest.core.event.ValidatingRepositoryEventListener
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer
import org.springframework.validation.Validator
import org.springframework.web.servlet.config.annotation.CorsRegistry
import se.svt.oss.encore.model.EncoreJob

@Configuration
class RepositoryConfiguration constructor(@Qualifier("defaultValidator") private val validator: Validator) : RepositoryRestConfigurer {
    override fun configureRepositoryRestConfiguration(config: RepositoryRestConfiguration, cors: CorsRegistry?) {
        config.exposeIdsFor(EncoreJob::class.java)
    }

    override fun configureValidatingRepositoryEventListener(validatingListener: ValidatingRepositoryEventListener?) {
        validatingListener?.addValidator("beforeCreate", validator)
    }
}
