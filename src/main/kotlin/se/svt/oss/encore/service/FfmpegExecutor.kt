// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.trySendBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.input.maxDuration
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.process.CommandBuilder
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.round

@Service
class FfmpegExecutor(private val mediaAnalyzer: MediaAnalyzer) {

    private val log = KotlinLogging.logger { }

    private val logLevelRegex = Regex(".*\\[(?<level>debug|info|warning|error)].*")

    fun getLoglevel(line: String) = logLevelRegex.matchEntire(line)?.groups?.get("level")?.value
    val progressRegex =
        Regex(".*frame= *(?<frame>[\\d+]+) fps= *(?<fps>[\\d.+]+) .* time=(?<hours>\\d{2}):(?<minutes>\\d{2}):(?<seconds>\\d{2}\\.\\d+) .* speed= *(?<speed>[0-9.e-]+x) *")

    fun run(
        encoreJob: EncoreJob,
        profile: Profile,
        outputs: List<Output>,
        outputFolder: String,
        progressChannel: SendChannel<Int>
    ): List<MediaFile> {
        val commands = CommandBuilder(encoreJob, profile, outputFolder).buildCommands(outputs)
        log.info { "Start encoding ${encoreJob.baseName}..." }
        val workDir = Files.createTempDirectory("encore_").toFile()
        val duration = encoreJob.duration ?: encoreJob.inputs.maxDuration()
        return try {
            File(outputFolder).mkdirs()
            progressChannel.trySendBlocking(0).getOrThrow()
            commands.forEachIndexed { index, command ->
                runFfmpeg(command, workDir, duration) { progress ->
                    progressChannel.trySendBlocking(totalProgress(progress, index, commands.size)).getOrThrow()
                }
            }
            progressChannel.close()
            outputs.flatMap { out ->
                if (out.fileFilter != null) {
                    File(outputFolder).listFiles(out.fileFilter)
                        .map { mediaAnalyzer.analyze(it.path) }
                } else {
                    listOf(mediaAnalyzer.analyze(File(outputFolder).resolve(out.output).toString()))
                }
            }
        } catch (e: CancellationException) {
            log.info { "Job was cancelled" }
            emptyList()
        } catch (e: Exception) {
            log.error(e) { "Failed Job" }
            throw e
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun runFfmpeg(
        command: List<String>,
        workDir: File,
        duration: Double?,
        onProgress: (Int) -> Unit
    ) {
        log.info { "Running duration: $duration" }
        log.info { command.joinToString(" ") }

        val ffmpegProcess = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

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
                                        "\n"
                                    )
                                    }"
                                )
                            }
                        }
                        "error" -> {
                            log.warn { line }
                            errorLines.add(line)
                        }
                        else -> log.debug { line }
                    }
                }
            }
        }

        finishProcess(ffmpegProcess, errorLines, onProgress)
        log.info { "Done" }
    }

    // https://stackoverflow.com/questions/37043114/how-to-stop-a-command-being-executed-after-4-5-seconds-through-process-builder
    private fun finishProcess(
        ffmpegProcess: Process,
        errorLines: MutableList<String>,
        onProgress: (Int) -> Unit
    ) {
        ffmpegProcess.waitFor(1L, TimeUnit.MINUTES)
        ffmpegProcess.destroy()

        val exitCode = ffmpegProcess.waitFor()
        if (exitCode != 0) {
            throw RuntimeException(
                "Error running ffmpeg (exit code $exitCode) :\n${errorLines.reversed().joinToString("\n")}"
            )
        }
        onProgress(100)
    }

    private fun totalProgress(subtaskProgress: Int, subtaskIndex: Int, subtaskCount: Int) =
        (subtaskIndex * 100 + subtaskProgress) / subtaskCount

    private fun getProgress(duration: Double?, line: String): Int? {
        return if (duration != null && duration > 0) {
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
    }
}
