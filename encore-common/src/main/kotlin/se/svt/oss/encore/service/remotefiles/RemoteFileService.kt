// SPDX-FileCopyrightText: 2024 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.remotefiles

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.net.URI

private val log = KotlinLogging.logger {}

@Service
class RemoteFileService(private val remoteFileHandlers: List<RemoteFileHandler>) {

    private val defaultHandler = DefaultHandler()

    fun isRemoteFile(uriOrPath: String): Boolean {
        val uri = URI.create(uriOrPath)
        return !(uri.scheme.isNullOrEmpty() || uri.scheme.lowercase() == "file")
    }

    fun getAccessUri(uriOrPath: String): String {
        val uri = URI.create(uriOrPath)
        return getHandler(uri).getAccessUri(uriOrPath)
    }

    fun upload(localFile: String, remoteFile: String) {
        val uri = URI.create(remoteFile)
        getHandler(uri).upload(localFile, remoteFile)
    }

    private fun getHandler(uri: URI): RemoteFileHandler {
        log.info { "Getting handler for uri $uri. Available protocols: ${remoteFileHandlers.flatMap {it.protocols} }" }
        if (uri.scheme.isNullOrEmpty() || uri.scheme.lowercase() == "file") {
            return defaultHandler
        }
        val handler = remoteFileHandlers.firstOrNull { it.protocols.contains(uri.scheme) }
        if (handler != null) {
            return handler
        }
        log.info { "No remote file handler found for protocol ${uri.scheme}. Using default handler." }
        return defaultHandler
    }

    /** Handler user for protocols where no specific handler is defined. Works for local files and
     * any protocols that ffmpeg supports natively */
    private class DefaultHandler : RemoteFileHandler {
        override fun getAccessUri(uri: String): String = uri

        override fun upload(localFile: String, remoteFile: String) {
            // Do nothing
        }

        override val protocols: List<String> = emptyList()
    }
}
