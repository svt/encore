// SPDX-FileCopyrightText: 2024 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.remotefiles.s3

import io.github.oshai.kotlinlogging.KotlinLogging
import se.svt.oss.encore.service.remotefiles.RemoteFileHandler
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.transfer.s3.S3TransferManager
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

class S3RemoteFileHandler(
    private val client: S3AsyncClient,
    private val presigner: S3Presigner,
    private val s3Properties: S3Properties,
    private val s3UriConverter: S3UriConverter,
    private val transferManager: S3TransferManager,
) : RemoteFileHandler {

    override fun getAccessUri(uri: String): String {
        val s3Uri = URI.create(uri)

        if (s3Properties.anonymousAccess) {
            return s3UriConverter.toHttp(s3Uri)
        }
        return presignUrl(s3Uri)
    }

    private fun presignUrl(s3Uri: URI): String {
        val (bucket, key) = s3UriConverter.getBucketAndKey(s3Uri)

        val objectRequest: GetObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
        val presignRequest: GetObjectPresignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(java.time.Duration.ofSeconds(s3Properties.presignDurationSeconds))
            .getObjectRequest(objectRequest)
            .build()

        val presignedRequest = presigner.presignGetObject(presignRequest)
        val url = presignedRequest.url().toExternalForm()
        return url
    }

    override fun upload(localFile: String, remoteFile: String) {
        log.info { "Uploading $localFile to $remoteFile" }
        val s3Uri = URI.create(remoteFile)
        val (bucket, key) = s3UriConverter.getBucketAndKey(s3Uri)
        if (s3Properties.anonymousAccess) {
            standardUpload(localFile, bucket, key)
        } else {
            transferManagerUpload(localFile, bucket, key)
        }
    }

    private fun transferManagerUpload(localFile: String, bucket: String, key: String) {
        val uploadRequest = UploadFileRequest.builder()
            .putObjectRequest { por -> por.bucket(bucket).key(key) }
            .addTransferListener(LoggingTransferListener.create())
            .source(Paths.get(localFile))
            .build()
        val fileUpload = transferManager.uploadFile(uploadRequest)
        val res = fileUpload.completionFuture().get(s3Properties.uploadTimeoutSeconds, TimeUnit.SECONDS)
        log.info { "Upload completed: $res" }
    }

    private fun standardUpload(localFile: String, bucket: String, key: String) {
        val putObjectRequest: PutObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
        val res = client.putObject(putObjectRequest, Paths.get(localFile))
            .get(s3Properties.presignDurationSeconds, TimeUnit.SECONDS)
        log.info { "Upload result: $res" }
    }

    override val protocols = listOf("s3")
}
