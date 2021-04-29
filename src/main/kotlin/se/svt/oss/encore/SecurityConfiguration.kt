// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.web.servlet.invoke
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import se.svt.oss.encore.config.EncoreProperties

private const val ROLE_USER = "USER"
private const val ROLE_ADMIN = "ADMIN"
private val ROLE_ANON = "ANON"

@ConditionalOnProperty(prefix = "encore-settings.security", name = ["enabled"])
@EnableWebSecurity
@Configuration
class SecurityConfiguration(
    private val encoreProperties: EncoreProperties
) : WebSecurityConfigurerAdapter() {

    override fun configure(auth: AuthenticationManagerBuilder) {
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        auth.inMemoryAuthentication()
            .withUser("user")
            .password(encoder.encode(encoreProperties.security.userPassword)).roles(ROLE_USER)
            .and()
            .withUser("admin").password(encoder.encode(encoreProperties.security.adminPassword)).roles(
                ROLE_USER,
                ROLE_ADMIN
            )
    }

    override fun configure(http: HttpSecurity) {
        http {
            headers { httpStrictTransportSecurity { } }
            authorizeRequests {
                authorize(HttpMethod.GET, "/health", anonymous)
                authorize(HttpMethod.GET, "/**", hasRole(ROLE_USER))
                authorize(HttpMethod.PATCH, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.PUT, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.DELETE, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.POST, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.PATCH, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.OPTIONS, "/**", hasRole(ROLE_ADMIN))
                authorize(HttpMethod.TRACE, "/**", hasRole(ROLE_ADMIN))
            }
            httpBasic { }
            csrf { disable() }
            anonymous {
                authorities = listOf(SimpleGrantedAuthority(ROLE_ANON))
            }
        }
    }
}
