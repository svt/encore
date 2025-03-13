// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.ImportRuntimeHints
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.service.EncoreService
import se.svt.oss.encore.service.queue.QueueService

private val log = KotlinLogging.logger { }

@EnableConfigurationProperties(EncoreProperties::class)
@ImportRuntimeHints(EncoreRuntimeHints::class)
@SpringBootApplication
class EncoreWorkerApplication(
    private val queueService: QueueService,
    private val encoreService: EncoreService,
    private val applicationContext: ApplicationContext,
    private val encoreProperties: EncoreProperties,
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        try {
            poll()
        } finally {
            log.info { "Stopping" }
            SpringApplication.exit(applicationContext)
        }
    }

    private fun poll() {
        val queueNo = encoreProperties.pollQueue ?: 0
        log.info { "Polling queue $queueNo" }
        val jobRun = queueService.poll(queueNo, encoreService::encode)
        if (encoreProperties.workerDrainQueue && jobRun) {
            poll()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<EncoreWorkerApplication>(* args)
}
