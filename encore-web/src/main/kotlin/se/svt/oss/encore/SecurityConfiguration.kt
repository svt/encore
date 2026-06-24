// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

@file:Suppress("DEPRECATION")

package se.svt.oss.encore

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import se.svt.oss.encore.config.EncoreProperties

private const val ROLE_USER = "USER"
private const val ROLE_ADMIN = "ADMIN"
private const val ROLE_HIERARCHY = "ROLE_ADMIN > ROLE_USER"

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration(private val encoreProperties: EncoreProperties) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        (encoder as? DelegatingPasswordEncoder)
            ?.setDefaultPasswordEncoderForMatches(NoOpPasswordEncoder.getInstance())
        return encoder
    }

    @Bean
    fun roleHierarchy(): RoleHierarchy =
        RoleHierarchyImpl.fromHierarchy(ROLE_HIERARCHY)

    @Bean
    fun users(): UserDetailsService {
        if (!encoreProperties.security.enabled) {
            return InMemoryUserDetailsManager()
        }
        require(encoreProperties.security.users.isNotEmpty()) {
            "Security is enabled but no users are configured"
        }
        val users = encoreProperties.security.users.map { (username, config) ->
            User.builder()
                .username(username)
                .password(config.password)
                .roles(config.role.name)
                .build()
        }
        return InMemoryUserDetailsManager(users)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            headers { httpStrictTransportSecurity { } }
            if (encoreProperties.security.enabled) {
                authorizeHttpRequests {
                    authorize(EndpointRequest.to(HealthEndpoint::class.java), permitAll)
                    authorize("/swagger-ui/**", permitAll)
                    authorize("/v3/api-docs/**", permitAll)
                    authorize(HttpMethod.GET, "/encoreJobs/**", hasRole(ROLE_USER))
                    authorize(HttpMethod.GET, "/queue", hasRole(ROLE_USER))
                    authorize(HttpMethod.GET, "/queueCount", hasRole(ROLE_USER))
                    authorize(HttpMethod.POST, "/encoreJobs", hasRole(ROLE_ADMIN))
                    authorize(HttpMethod.POST, "/encoreJobs/*/cancel", hasRole(ROLE_ADMIN))
                    authorize(anyRequest, denyAll)
                }
                httpBasic { }
            } else {
                authorizeHttpRequests {
                    authorize(anyRequest, permitAll)
                }
            }
            csrf { disable() }
        }
        return http.build()
    }
}
