// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import se.svt.oss.encore.handlers.EncoreJobHandler

class EncoreWebRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.reflection()
            .registerType(
                EncoreJobHandler::class.java,
                MemberCategory.INVOKE_PUBLIC_METHODS,
            )
    }
}
