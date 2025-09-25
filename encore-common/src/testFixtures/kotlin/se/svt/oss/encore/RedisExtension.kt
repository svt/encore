package se.svt.oss.encore

import com.redis.testcontainers.RedisContainer
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.utility.DockerImageName

private const val DEFAULT_REDIS_DOCKER_IMAGE = "redis:6.2.13"

class RedisExtension : BeforeAllCallback {
    private val log = KotlinLogging.logger { }
    override fun beforeAll(context: ExtensionContext?) {
        if (isDockerAvailable()) {
            val dockerImageName = System.getenv("ENCORE_REDIS_DOCKER_IMAGE") ?: DEFAULT_REDIS_DOCKER_IMAGE
            val redisContainer = RedisContainer(DockerImageName.parse(dockerImageName))
                .withKeyspaceNotifications()
            redisContainer.start()
            val host = redisContainer.redisHost
            val port = redisContainer.redisPort.toString()
            log.info { "Setting spring.data.redis.host=$host" }
            log.info { "Setting spring.data.redis.port=$port" }
            System.setProperty("spring.data.redis.host", host)
            System.setProperty("spring.data.redis.port", port)
        }
    }
}
