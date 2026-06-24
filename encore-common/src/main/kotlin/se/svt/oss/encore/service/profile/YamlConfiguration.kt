// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.profile

import org.springframework.boot.jackson.JacksonComponentModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.dataformat.yaml.YAMLMapper

@Configuration(proxyBeanMethods = false)
class YamlConfiguration {

    @Bean
    fun yamlMapper(jacksonComponentModule: JacksonComponentModule) = YAMLMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .findAndAddModules()
        .addModules(jacksonComponentModule)
        .build()
}
