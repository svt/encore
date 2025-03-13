// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import se.svt.oss.encore.service.callback.CallbackClient

@Configuration(proxyBeanMethods = false)
class ClientConfiguration {

    @Bean
    fun callbackClient(@Value("\${service.name:encore}") userAgent: String): CallbackClient {
        val webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
            .build()
            .createClient(CallbackClient::class.java)
    }
}
