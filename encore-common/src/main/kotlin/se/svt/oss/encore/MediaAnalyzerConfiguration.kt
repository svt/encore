// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.svt.oss.mediaanalyzer.MediaAnalyzer

@Configuration
class MediaAnalyzerConfiguration {

    @Bean
    fun mediaAnalyzer(objectMapper: ObjectMapper) = MediaAnalyzer(objectMapper)
}
