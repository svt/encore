// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import io.github.oshai.kotlinlogging.KotlinLogging
import se.svt.oss.encore.model.output.Output

private val log = KotlinLogging.logger { }

abstract class AudioEncoder : OutputProducer {

    abstract val optional: Boolean
    abstract val codec: String
    abstract override val enabled: Boolean

    fun logOrThrow(message: String): Output? {
        if (optional || !enabled) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
