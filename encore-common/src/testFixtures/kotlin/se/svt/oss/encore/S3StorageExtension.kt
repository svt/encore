// SPDX-FileCopyrightText: 2024 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.utility.DockerImageName

class S3StorageExtension : BeforeAllCallback {
    private val log = KotlinLogging.logger { }
    override fun beforeAll(context: ExtensionContext?) {
        if (!isDockerAvailable()) {
            log.warn { "Docker is not available! Make sure minio is available as configured by remote-files.s3.*" }
            return
        }
        val localstackImage = DockerImageName.parse("localstack/localstack:3.5.0")

        val localstack: LocalStackContainer = LocalStackContainer(localstackImage)
            .withServices(LocalStackContainer.Service.S3)
        localstack.start()

        log.info { "localstack endpoint: ${localstack.endpoint}" }
        System.setProperty("aws.accessKeyId", localstack.accessKey)
        System.setProperty("aws.secretAccessKey", localstack.secretKey)
        System.setProperty("remote-files.s3.endpoint", localstack.endpoint.toString())
    }
}
