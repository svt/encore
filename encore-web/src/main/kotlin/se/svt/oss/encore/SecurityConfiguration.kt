// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest
import org.springframework.boot.actuate.health.HealthEndpoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import se.svt.oss.encore.config.EncoreProperties

private const val ROLE_USER = "USER"
private const val ROLE_ADMIN = "ADMIN"
private const val ROLE_ANON = "ANON"

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnProperty(prefix = "encore-settings.security", name = ["enabled"])
class SecurityConfiguration(private val encoreProperties: EncoreProperties) {

    @Bean
    fun users(): UserDetailsService {
        val user = User.builder()
            .username("user")
            .password(encoreProperties.security.userPassword)
            .roles(ROLE_USER)
            .build()
        val admin = User.builder()
            .username("admin")
            .password(encoreProperties.security.adminPassword)
            .roles(ROLE_USER, ROLE_ADMIN)
            .build()
        return InMemoryUserDetailsManager(user, admin)
    }

    @Bean
    fun filterChain(http: HttpSecurity, webEndPointProperties: WebEndpointProperties): SecurityFilterChain {
        http {
            headers { httpStrictTransportSecurity { } }
            authorizeRequests {
                authorize(EndpointRequest.to(HealthEndpoint::class.java), permitAll)
                authorize(HttpMethod.GET, "/**", hasRole(ROLE_USER))
                authorize(HttpMethod.PUT, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.DELETE, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.POST, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.PATCH, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.OPTIONS, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.TRACE, "/**", hasRole(ROLE_ADMIN))
                authorize(anyRequest, denyAll)
            }
            httpBasic { }
            csrf { disable() }
            anonymous {
                authorities = listOf(SimpleGrantedAuthority(ROLE_ANON))
            }
        }
        return http.build()
    }
}
