// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import feign.RequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

@Configuration
@EnableFeignClients
class FeignConfiguration {

    @Bean
    fun userAgentInterceptor(@Value("\${service.name:encore}") userAgent: String): RequestInterceptor =
        RequestInterceptor { template -> template.header(HttpHeaders.USER_AGENT, userAgent) }
}
