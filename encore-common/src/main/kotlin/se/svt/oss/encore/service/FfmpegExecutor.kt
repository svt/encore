// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import org.springframework.stereotype.Service
import se.svt.oss.encore.config.EncoreProperties
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.maxDuration
import se.svt.oss.encore.model.mediafile.toParams
import se.svt.oss.encore.process.CommandBuilder
import se.svt.oss.encore.process.createTempDir
import se.svt.oss.encore.service.profile.ProfileService
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.round

private val log = KotlinLogging.logger { }

@Service
class FfmpegExecutor(
    private val mediaAnalyzer: MediaAnalyzer,
    private val profileService: ProfileService,
    private val encoreProperties: EncoreProperties,
) {

    private val logLevelRegex = Regex(".*\\[(?<level>debug|info|warning|error|fatal)].*")

    fun getLoglevel(line: String) = logLevelRegex.matchEntire(line)?.groups?.get("level")?.value

    val progressRegex =
        Regex(".*time=(?<hours>\\d{2}):(?<minutes>\\d{2}):(?<seconds>\\d{2}\\.\\d+) .* speed= *(?<speed>[0-9.e-]+x) .*")

    fun run(
        encoreJob: EncoreJob,
        outputFolder: String,
        progressChannel: SendChannel<Int>?,
    ): List<MediaFile> {
        ShutdownHandler.checkShutdown()
        val profile = profileService.getProfile(encoreJob)
        val outputs = profile.encodes.mapNotNull {
            it.getOutput(
                encoreJob,
                encoreProperties.encoding,
                profile.filterSettings,
            )
        }
        check(outputs.isNotEmpty()) {
            "No outputs to encode! Check your profile and inputs!"
        }
        check(outputs.distinctBy { it.id }.size == outputs.size) {
            "Profile ${encoreJob.profile} contains duplicate suffixes: ${outputs.map { it.id }}!"
        }
        val commands =
            CommandBuilder(encoreJob, profile, outputFolder, encoreProperties.encoding).buildCommands(outputs)
        log.info { "Start encoding ${encoreJob.baseName}..." }
        val workDir = createTempDir("encore_").toFile()
        val duration = encoreJob.duration ?: encoreJob.inputs.maxDuration()
        return try {
            File(outputFolder).mkdirs()
            commands.forEachIndexed { index, command ->
                runFfmpeg(command, workDir, duration) { progress ->
                    progressChannel?.trySendBlocking(totalProgress(progress, index, commands.size))?.getOrThrow()
                }
            }
            progressChannel?.close()
            outputs.flatMap { out ->
                out.postProcessor.process(File(outputFolder))
                    .map { mediaAnalyzer.analyze(it.toString(), disableImageSequenceDetection = out.isImage) }
            }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun runFfmpeg(
        command: List<String>,
        workDir: File,
        duration: Double?,
        onProgress: (Int) -> Unit,
    ) {
        ShutdownHandler.checkShutdown()
        onProgress(0)
        log.debug { "Running duration: $duration" }
        log.info { command.joinToString(" ") }

        val ffmpegProcess = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        val shutdownHook = Thread {
            log.info { "Application is shutting down. Stopping encoding." }
            ffmpegProcess.destroy()
            if (!ffmpegProcess.waitFor(3, TimeUnit.SECONDS)) {
                log.debug { "Ffmpeg did not shut down in 3 seconds. Destroying forcibly." }
                ffmpegProcess.destroyForcibly()
            }
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            val errorLines = mutableListOf<String>()
            ffmpegProcess.inputStream.reader().useLines { lines ->
                lines.forEach { line ->
                    val progress = getProgress(duration, line)
                    if (progress != null) {
                        onProgress(progress)
                    } else {
                        when (getLoglevel(line)) {
                            "warning" -> {
                                if (!line.contains("Adaptive color transform in an unsupported profile.")) {
                                    log.warn { line }
                                }
                                if (line.contains("DPB size")) {
                                    errorLines.add(line)
                                    throw RuntimeException(
                                        "Coding might not be compatible on all devices:\n${
                                            errorLines.joinToString(
                                                "\n",
                                            )
                                        }",
                                    )
                                }
                            }

                            "error", "fatal" -> {
                                log.warn { line }
                                errorLines.add(line)
                            }

                            else -> log.debug { line }
                        }
                    }
                }
            }

            val exitCode = ffmpegProcess.waitFor()

            if (exitCode != 0) {
                throw RuntimeException(
                    "Error running ffmpeg (exit code $exitCode) :\n${errorLines.reversed().joinToString("\n")}",
                )
            }
            onProgress(100)
            log.info { "Done" }
        } catch (e: Exception) {
            ShutdownHandler.checkShutdown()
            throw e
        } finally {
            if (!ShutdownHandler.isShutDown()) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook)
                } catch (_: IllegalStateException) {
                }
            }
        }
    }

    private fun totalProgress(subtaskProgress: Int, subtaskIndex: Int, subtaskCount: Int) =
        (subtaskIndex * 100 + subtaskProgress) / subtaskCount

    private fun getProgress(duration: Double?, line: String): Int? = if (duration != null && duration > 0) {
        progressRegex.matchEntire(line)?.let {
            val hours = it.groups["hours"]!!.value.toInt()
            val minutes = it.groups["minutes"]!!.value.toInt()
            val seconds = it.groups["seconds"]!!.value.toDouble()
            val time = hours * 3600 + minutes * 60 + seconds
            min(100, round(100 * time / duration).toInt())
        }
    } else {
        null
    }

    fun joinSegments(encoreJob: EncoreJob, segmentList: File, targetFile: File): MediaFile {
        val joinParams = profileService.getProfile(encoreJob).joinSegmentParams.toParams()
        val command = listOf(
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "+level",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            "$segmentList",
            "-map",
            "0",
            "-ignore_unknown",
            "-c",
            "copy",
            "-metadata",
            "comment=Transcoded using Encore",
        ) + joinParams + "$targetFile"
        runFfmpeg(command, segmentList.parentFile, null) {}
        return mediaAnalyzer.analyze(targetFile.absolutePath)
    }
}
