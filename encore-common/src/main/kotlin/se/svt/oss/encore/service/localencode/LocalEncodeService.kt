// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.localencode

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.process.createTempDir
import se.svt.oss.encore.service.remotefiles.RemoteFileService
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.ImageFile
import se.svt.oss.mediaanalyzer.file.MediaFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

@Service
class LocalEncodeService(
    private val encoreProperties: EncoreProperties,
    private val remoteFileService: RemoteFileService,
) {

    fun outputFolder(
        encoreJob: EncoreJob,
    ): String = if (encoreProperties.localTemporaryEncode || remoteFileService.isRemoteFile(encoreJob.outputFolder)) {
        createTempDir("job_${encoreJob.id}").toString()
    } else {
        encoreJob.outputFolder
    }

    fun localEncodedFilesToCorrectDir(
        outputFolder: String,
        output: List<MediaFile>,
        encoreJob: EncoreJob,
    ): List<MediaFile> {
        if (remoteFileService.isRemoteFile(encoreJob.outputFolder)) {
            log.debug { "Moving files to output destination ${encoreJob.outputFolder}, from local temp $outputFolder" }
            File(outputFolder).listFiles()?.forEach { localFile ->
                val remoteFile = URI.create(encoreJob.outputFolder).resolve(localFile.name).toString()
                remoteFileService.upload(localFile.toString(), remoteFile)
            }
            val files = output.map {
                val resolvedPath = URI.create(encoreJob.outputFolder).resolve(Path.of(it.file).fileName.toString()).toString()
                when (it) {
                    is VideoFile -> it.copy(file = resolvedPath)
                    is AudioFile -> it.copy(file = resolvedPath)
                    is ImageFile -> it.copy(file = resolvedPath)
                    else -> throw Exception("Invalid conversion")
                }
            }
            return files
        }
        if (encoreProperties.localTemporaryEncode) {
            val destination = File(encoreJob.outputFolder)
            log.debug { "Moving files to correct outputFolder ${encoreJob.outputFolder}, from local temp $outputFolder" }
            if (!destination.exists()) destination.mkdirs()
            moveTempLocalFiles(destination, outputFolder)
            val files = resolveMovedOutputFiles(output, encoreJob)
            log.debug { "Locally encoded files have been successfully moved to output folder." }
            return files
        }
        return output
    }

    fun cleanup(tempDirectory: String?, encoreJob: EncoreJob) {
        if (tempDirectory != null &&
            (encoreProperties.localTemporaryEncode || remoteFileService.isRemoteFile(encoreJob.outputFolder))
        ) {
            File(tempDirectory).deleteRecursively()
        }
    }

    private fun moveTempLocalFiles(destination: File, tempDirectory: String) {
        File(tempDirectory).listFiles()?.forEach { moveFile(it, destination) }
    }

    private fun moveFile(file: File, destination: File) {
        try {
            executeMoveFile(file, destination)
        } catch (e: Exception) {
            log.debug { "Error when moving file ${file.path}. Trying again. message= ${e.message}" }
            executeMoveFile(file, destination)
        }
    }

    private fun executeMoveFile(file: File, destination: File) {
        log.debug { "Moving file ${file.path}" }
        Files.move(file.toPath(), destination.resolve(file.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun resolveMovedOutputFiles(output: List<MediaFile>, encoreJob: EncoreJob): List<MediaFile> = output.map { file ->
        when (file) {
            is VideoFile -> file.copy(file = resolvePath(file, encoreJob))
            is AudioFile -> file.copy(file = resolvePath(file, encoreJob))
            is ImageFile -> file.copy(file = resolvePath(file, encoreJob))
            else -> throw Exception("Invalid conversion")
        }
    }

    private fun resolvePath(file: MediaFile, encoreJob: EncoreJob): String {
        val path = Path.of(file.file)
        return path.resolveSibling("${encoreJob.outputFolder}/${path.fileName}").toString()
    }
}
