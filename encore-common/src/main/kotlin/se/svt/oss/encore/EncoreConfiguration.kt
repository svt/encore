// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.config.EncoreProperties

@EnableConfigurationProperties(EncoreProperties::class)
@RegisterReflectionForBinding(
    EncoreProperties::class,
    EncodingProperties::class,
    AudioMixPreset::class,
)
@Configuration
class EncoreConfiguration
