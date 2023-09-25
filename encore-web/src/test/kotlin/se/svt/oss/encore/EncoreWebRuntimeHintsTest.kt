// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.handlers.EncoreJobHandler
import kotlin.reflect.jvm.javaMethod

class EncoreWebRuntimeHintsTest {
    @Test
    fun shouldRegisterHints() {
        val hints = RuntimeHints()
        EncoreWebRuntimeHints().registerHints(hints, javaClass.classLoader)
        assertThat(RuntimeHintsPredicates.reflection().onMethod(EncoreJobHandler::onAfterCreate.javaMethod!!)).accepts(hints)
    }
}
