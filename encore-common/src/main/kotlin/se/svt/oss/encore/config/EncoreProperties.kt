// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.io.File
import java.time.Duration

@ConfigurationProperties("encore-settings")
data class EncoreProperties(
    /**
     * transcode to local tmp dir before copying to output folder
     */
    val localTemporaryEncode: Boolean = false,
    /**
     * number of work queues and threads
     */
    val concurrency: Int = 2,
    /**
     * time to wait after application start before polling queue
     */
    val pollInitialDelay: Duration = Duration.ofSeconds(10),
    /**
     * time to wait between polls
     */
    val pollDelay: Duration = Duration.ofSeconds(5),
    /**
     * poll only the specified queue
     */
    val pollQueue: Int? = null,
    /**
     * disable polling. could be set on encore-web if all transcoding is to be done by encore-workers
     */
    val pollDisabled: Boolean = false,
    /**
     * should queues with higher prio be poller before the queue assigned to thread or worker
     */
    val pollHigherPrio: Boolean = true,
    /**
     * if true, encore-worker will poll the queue until empty before shutting down, otherwise just poll once
     */
    val workerDrainQueue: Boolean = false,
    val redisKeyPrefix: String = "encore",
    /**
     * optional web security settings
     */
    val security: Security = Security(),
    /**
     * open api contact information
     */
    val openApi: OpenApi = OpenApi(),
    /**
     * path to directory shared by encore instances. required for encoding in segments
     */
    val sharedWorkDir: File? = null,
    /**
     * timeout for segmented encode before failing
     */
    val segmentedEncodeTimeout: Duration = Duration.ofMinutes(120),
    /***
     * enable migration of queues from redis LIST to ZSET
     */
    val queueMigrationScriptEnabled: Boolean = true,
    @NestedConfigurationProperty
    val encoding: EncodingProperties = EncodingProperties(),
) {
    data class Security(
        val enabled: Boolean = false,
        val userPassword: String = "",
        val adminPassword: String = "",
    )

    data class OpenApi(
        val title: String = "Encore OpenAPI",
        val description: String = "Endpoints for Encore",
        val contactName: String = "",
        val contactUrl: String = "",
        val contactEmail: String = "",
    )
}
