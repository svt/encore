// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.sendBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.output.Output
import se.svt.oss.encore.model.profile.Profile
import se.svt.oss.encore.process.CommandBuilder
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

@Service
class FfmpegExecutor(private val mediaAnalyzer: MediaAnalyzer) {

    private val log = KotlinLogging.logger { }

    private val logLevelRegex = Regex(".*\\[(?<level>debug|info|warning|error)].*")

    fun getLoglevel(line: String) = logLevelRegex.matchEntire(line)?.groups?.get("level")?.value
    val progressRegex = Regex(".*frame= *(?<frame>[\\d+]+) fps= *(?<fps>[\\d.+]+) .* speed= *(?<speed>[0-9.e-]+x) *")

    fun run(
        encoreJob: EncoreJob,
        profile: Profile,
        outputs: List<Output>,
        progressChannel: SendChannel<Int>
    ): List<MediaFile> {
        val commands = CommandBuilder(encoreJob, profile).buildCommands(outputs)
        log.info { "Start encoding ${encoreJob.filename}..." }
        val workDir = Files.createTempDirectory("encore_").toFile()

        return try {
            outputs.forEach { File(it.output).parentFile.mkdirs() }
            commands.forEachIndexed { index, command ->
                runFfmpeg(command, workDir, encoreJob.inputOrThrow.highestBitrateVideoStream.numFrames) {
                    progressChannel.sendBlocking(totalProgress(it, index, commands.size))
                }
            }
            progressChannel.close()
            outputs.flatMap { out ->
                if (out.fileFilter != null) {
                    File(out.output).parentFile.listFiles(out.fileFilter)
                        .map { mediaAnalyzer.analyze(it.path) }
                } else {
                    listOf(mediaAnalyzer.analyze(out.output))
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
        numFrames: Int,
        onProgress: (Int) -> Unit
    ) {
        log.info { "Running" }
        log.info { command.joinToString(" ") }

        val ffmpegProcess = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()

        val errorLines = mutableListOf<String>()
        ffmpegProcess.inputStream.reader().useLines { lines ->
            lines.forEach { line ->
                val progress = getProgress(numFrames, line)
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
                                    "Coding might not be compatible on all devices:\n${errorLines.joinToString(
                                        "\n"
                                    )}"
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

    private fun getProgress(numFrames: Int, line: String): Int? {
        return if (numFrames > 0) {
            progressRegex.matchEntire(line)?.let { matchResult ->
                val frame = matchResult.groups["frame"]?.value?.toInt() ?: 0
                100 * frame / numFrames
            }
        } else {
            null
        }
    }
}
