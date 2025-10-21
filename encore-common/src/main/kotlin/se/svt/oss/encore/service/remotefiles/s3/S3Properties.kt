// SPDX-FileCopyrightText: 2024 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.remotefiles.s3

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

const val MB = 1024 * 1024L

data class MultipartUploadProperties(
    val minimumPartSize: Long? = null,
    val threshold: Long? = null,
    val targetThroughputGbps: Double? = null,
    val maxConcurrency: Int? = null,
)

@ConfigurationProperties("remote-files.s3")
data class S3Properties(
    val enabled: Boolean = false,
    val anonymousAccess: Boolean = false,
    val usePathStyle: Boolean = true,
    val endpoint: String = "",
    val presignDurationSeconds: Long = Duration.ofHours(12).seconds,
    val uploadTimeoutSeconds: Long = Duration.ofHours(1).seconds,
    @NestedConfigurationProperty
    val multipart: MultipartUploadProperties = MultipartUploadProperties(),
)
