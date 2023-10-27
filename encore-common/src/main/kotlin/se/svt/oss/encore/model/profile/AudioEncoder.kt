// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.profile

import mu.KotlinLogging
import se.svt.oss.encore.model.output.Output

private val log = KotlinLogging.logger { }

abstract class AudioEncoder : OutputProducer {

    abstract val optional: Boolean

    override val type: String
        get() = this.javaClass.simpleName

    fun logOrThrow(message: String): Output? {
        if (optional) {
            log.info { message }
            return null
        } else {
            throw RuntimeException(message)
        }
    }
}
