package se.svt.oss.encore

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@TestConfiguration(proxyBeanMethods = false)
@Lazy
class TestConfig {

    @Bean
    fun encoreClient(@Value("\${local.server.port}") localPort: Int): EncoreClient {
        return HttpServiceProxyFactory
            .builder(WebClientAdapter.forClient(WebClient.create("http://localhost:$localPort")))
            .build()
            .createClient(EncoreClient::class.java)
    }
}
