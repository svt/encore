// SPDX-FileCopyrightText: 2021 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.redis.testcontainers.RedisContainer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.DockerClientFactory
import org.testcontainers.utility.DockerImageName

private const val DEFAULT_REDIS_DOCKER_IMAGE = "redis:8.6-alpine"

private val log = KotlinLogging.logger { }

class RedisExtension :
    BeforeAllCallback,
    AfterAllCallback {

    private var container: RedisContainer? = null

    override fun beforeAll(context: ExtensionContext) {
        if (isDockerAvailable()) {
            val dockerImageName = System.getenv("ENCORE_REDIS_DOCKER_IMAGE") ?: DEFAULT_REDIS_DOCKER_IMAGE
            val redisContainer = RedisContainer(DockerImageName.parse(dockerImageName))
            redisContainer.start()
            log.info { "Setting encore-settings.redis.uri=${redisContainer.redisURI}" }
            System.setProperty("encore-settings.redis.uri", redisContainer.redisURI)
            container = redisContainer
        }
    }

    private fun isDockerAvailable(): Boolean = try {
        log.info { "Checking for docker..." }
        DockerClientFactory.instance().client()
        log.info { "Docker is available" }
        true
    } catch (_: Throwable) {
        log.warn { "Docker is not available! Make sure redis is available as configured by encore-settings.redis.uri (default redis://localhost:6379)" }
        false
    }

    // Stop explicitly if testcontainers Ryuk is disabled. Redundant but harmless if enabled.
    override fun afterAll(context: ExtensionContext) {
        container?.stop()
    }
}
