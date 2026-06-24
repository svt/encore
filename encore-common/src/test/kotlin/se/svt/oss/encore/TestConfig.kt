// SPDX-FileCopyrightText: 2023 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webclient.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.data.web.config.EnableSpringDataWebSupport
import org.springframework.hateoas.config.HypermediaWebClientConfigurer
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient

@TestConfiguration(proxyBeanMethods = false)
@Lazy
@EnableSpringDataWebSupport
class TestConfig {

    @Bean
    fun hypermediaWebClientCustomizer(configurer: HypermediaWebClientConfigurer) = WebClientCustomizer {
        configurer.registerHypermediaTypes(it)
    }

    @Bean
    fun encoreClient(
        @Value("\${local.server.port}") localPort: Int,
        webClientBuilder: WebClient.Builder,
    ): EncoreClient = HttpServiceProxyFactory
        .builderFor(
            WebClientAdapter.create(
                webClientBuilder
                    .baseUrl("http://localhost:$localPort").build(),
            ),
        )
        .build()
        .createClient<EncoreClient>()
}
