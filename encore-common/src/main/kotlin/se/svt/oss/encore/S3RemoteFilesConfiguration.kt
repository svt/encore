// SPDX-FileCopyrightText: 2024 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import se.svt.oss.encore.service.remotefiles.s3.S3Properties
import se.svt.oss.encore.service.remotefiles.s3.S3RemoteFileHandler
import se.svt.oss.encore.service.remotefiles.s3.S3UriConverter
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.transfer.s3.S3TransferManager
import java.net.URI

@ConditionalOnProperty("remote-files.s3.enabled", havingValue = "true")
@EnableConfigurationProperties(S3Properties::class)
@Configuration
class S3RemoteFilesConfiguration {

    @Bean
    fun s3Region() =
        Region.of(System.getProperty("aws.region") ?: System.getenv("AWS_REGION") ?: "us-east-1")

    @Bean
    fun transferManager(s3Client: S3AsyncClient, s3Properties: S3Properties): S3TransferManager =
        S3TransferManager.builder()
            .s3Client(s3Client)
            .build()

    @Bean
    fun s3Client(s3Region: Region, s3Properties: S3Properties) =
        if (s3Properties.anonymousAccess) {
            // Use standard s3 client for anonymous access since it supports
            // disabling multipart
            S3AsyncClient.builder()
                .forcePathStyle(s3Properties.usePathStyle)
                .crossRegionAccessEnabled(true)
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .multipartEnabled(false) // Multipart is not supported with anonymous access
                .region(s3Region)
                .apply {
                    if (s3Properties.endpoint.isNotBlank()) {
                        endpointOverride(URI.create(s3Properties.endpoint))
                    }
                }
                .build()
        } else {
            S3AsyncClient.crtBuilder()
                .region(s3Region)
                .crossRegionAccessEnabled(true)
                .forcePathStyle(s3Properties.usePathStyle)
                .maxConcurrency(s3Properties.multipart.maxConcurrency)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .minimumPartSizeInBytes(s3Properties.multipart.minimumPartSize)
                .thresholdInBytes(s3Properties.multipart.threshold)
                .targetThroughputInGbps(s3Properties.multipart.targetThroughputGbps)
                .apply {
                    if (s3Properties.endpoint.isNotBlank()) {
                        endpointOverride(URI.create(s3Properties.endpoint))
                    }
                }
                .build()
        }

    @Bean
    fun s3Presigner(s3Region: Region, s3Properties: S3Properties) = S3Presigner.builder()
        .region(s3Region)
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build(),
        )
        .apply {
            if (!s3Properties.endpoint.isNullOrBlank()) {
                endpointOverride(URI.create(s3Properties.endpoint))
            }
        }
        .build()

    @Bean
    fun s3UriConverter(s3Properties: S3Properties, s3Region: Region) = S3UriConverter(s3Properties, s3Region)

    @Bean
    fun s3RemoteFileHandler(
        s3Client: S3AsyncClient,
        s3Presigner: S3Presigner,
        s3Properties: S3Properties,
        s3UriConverter: S3UriConverter,
        transferManager: S3TransferManager,
    ) =
        S3RemoteFileHandler(s3Client, s3Presigner, s3Properties, s3UriConverter, transferManager)
}
