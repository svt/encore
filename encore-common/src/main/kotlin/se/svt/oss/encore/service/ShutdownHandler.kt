// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextClosedEvent
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger {}

@Component
class ShutdownHandler : ApplicationListener<ContextClosedEvent> {
    companion object {
        private val isShutDown = AtomicBoolean(false)
        fun isShutDown() = isShutDown.get()
        fun checkShutdown() {
            if (isShutDown()) {
                throw ApplicationShutdownException()
            }
        }
    }

    @PostConstruct
    fun addHook() {
        Runtime.getRuntime().addShutdownHook(Thread { isShutDown.set(true) })
    }

    override fun onApplicationEvent(event: ContextClosedEvent) {
        if (isShutDown()) {
            log.info { "Delaying application shutdown" }
            Thread.sleep(6000)
            log.info { "Continue application shutdown" }
        }
    }

    override fun supportsAsyncExecution() = false
}
