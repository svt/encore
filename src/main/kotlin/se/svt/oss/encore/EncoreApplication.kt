// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.boot.SpringApplication
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.retry.annotation.EnableRetry
import se.svt.oss.encore.config.EncoreProperties

@EnableRetry
@EnableConfigurationProperties(EncoreProperties::class)
@SpringBootApplication(
    exclude = [SecurityAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class]
)
class EncoreApplication

fun main(args: Array<String>) {
    SpringApplication.run(EncoreApplication::class.java, *args)
}
