// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.config.AudioMixPreset
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.config.EncoreProperties
import kotlin.reflect.jvm.javaConstructor

class EncoreRuntimeHintsTest {
    @Test
    fun shouldRegisterHints() {
        val hints = RuntimeHints()
        EncoreRuntimeHints().registerHints(hints, javaClass.classLoader)
        assertThat(
            RuntimeHintsPredicates.reflection().onConstructor(EncoreProperties::class.constructors.first().javaConstructor!!)
        ).accepts(hints)
        assertThat(
            RuntimeHintsPredicates.reflection().onConstructor(EncodingProperties::class.constructors.first().javaConstructor!!)
        ).accepts(hints)
        assertThat(
            RuntimeHintsPredicates.reflection().onConstructor(AudioMixPreset::class.constructors.first().javaConstructor!!)
        ).accepts(hints)
    }
}
