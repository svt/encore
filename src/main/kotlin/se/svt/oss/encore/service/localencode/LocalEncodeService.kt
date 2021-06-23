// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service.localencode

import mu.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.mediaanalyzer.file.AudioFile
import se.svt.oss.mediaanalyzer.file.ImageFile
import se.svt.oss.mediaanalyzer.file.MediaFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Service
class LocalEncodeService(
    private val encoreProperties: EncoreProperties
) {

    private val log = KotlinLogging.logger {}

    fun outputFolder(
        encoreJob: EncoreJob
    ): String {
        return if (encoreProperties.localTemporaryEncode) {
            Files.createTempDirectory("job_${encoreJob.id}").toString()
        } else {
            encoreJob.outputFolder
        }
    }

    fun localEncodedFilesToCorrectDir(
        outputFolder: String,
        output: List<MediaFile>,
        encoreJob: EncoreJob
    ): List<MediaFile> {

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

    fun cleanup(tempDirectory: String?) {
        if (tempDirectory != null && encoreProperties.localTemporaryEncode) {
            File(tempDirectory).deleteRecursively()
        }
    }

    private fun moveTempLocalFiles(destination: File, tempDirectory: String) {
        val filesBeforeMove = File(tempDirectory).listFiles()
        filesBeforeMove?.forEach { moveFile(it, destination) }
        val fileCountAfterMove = destination.list()?.size
        if (fileCountAfterMove != filesBeforeMove?.count()) {
            throw RuntimeException("File count after moving files from temp to output folder differs.")
        }
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

    private fun resolveMovedOutputFiles(output: List<MediaFile>, encoreJob: EncoreJob): List<MediaFile> {
        return output.map { file ->
            when (file) {
                is VideoFile -> file.copy(file = resolvePath(file, encoreJob))
                is AudioFile -> file.copy(file = resolvePath(file, encoreJob))
                is ImageFile -> file.copy(file = resolvePath(file, encoreJob))
                else -> throw Exception("Invalid conversion")
            }
        }
    }

    private fun resolvePath(file: MediaFile, encoreJob: EncoreJob): String {
        val path = Path.of(file.file)
        return path.resolveSibling("${encoreJob.outputFolder}/${path.fileName}").toString()
    }
}
