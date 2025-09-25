package se.svt.oss.encore

import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.DockerClientFactory

private val log = KotlinLogging.logger { }

fun isDockerAvailable(): Boolean = try {
    log.info { "Checking for docker..." }
    DockerClientFactory.instance().client()
    log.info { "Docker is available" }
    true
} catch (ex: Throwable) {
    log.warn { "Docker is not available! Make sure redis is available as configured by spring.data.redis (default localhost:6379)" }
    false
}
