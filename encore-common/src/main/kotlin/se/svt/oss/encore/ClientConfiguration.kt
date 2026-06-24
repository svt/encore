// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import reactor.netty.http.client.HttpClient
import se.svt.oss.encore.service.callback.CallbackClient
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class ClientConfiguration {

    @Bean
    fun callbackClient(
        @Value("\${service.name:encore}")
        userAgent: String,
        clientBuilder: WebClient.Builder,
    ): CallbackClient {
        val httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(2))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
        val connector = ReactorClientHttpConnector(httpClient)
        val webClient = clientBuilder
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .clientConnector(connector)
            .build()
        return HttpServiceProxyFactory.builderFor(WebClientAdapter.create(webClient))
            .build()
            .createClient<CallbackClient>()
    }
}
