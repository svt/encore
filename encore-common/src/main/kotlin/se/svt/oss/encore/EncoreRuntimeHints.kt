// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.config.EncoreProperties

class EncoreRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection()
            .registerType(
                EncoreProperties::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            )
            .registerType(
                EncodingProperties::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            )
            .registerType(
                AudioMixPreset::class.java,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
            )
    }
}
