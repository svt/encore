// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import org.springframework.validation.annotation.Validated
import se.svt.oss.mediaanalyzer.file.FractionString
import se.svt.oss.mediaanalyzer.file.MediaFile
import se.svt.oss.mediaanalyzer.file.VideoFile
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

private const val AR_REGEX = "^[1-9]\\d*[:/][1-9]\\d*$"
private const val AR_MESSAGE = "Must be positive fraction, e.g. 16:9"

@Validated
@RedisHash("encore-jobs", timeToLive = 60 * 60 * 24 * 7) // 1 week ttl
@Tag(name = "encorejob")
data class EncoreJob(

    @Schema(
        description = "The Encore Internal EncoreJob Identity", example = "fb2baa17-8972-451b-bb1e-1bc773283476",
        accessMode = Schema.AccessMode.READ_ONLY, hidden = false, defaultValue = "A random UUID"
    )
    @Id val id: UUID = UUID.randomUUID(),

    @Schema(
        description = "External id - for external backreference", example = "any-string",
        nullable = true
    )
    val externalId: String? = null,

    @Schema(
        description = "The input file that the EncoreJob should process",
        example = "/path/to/a/file/file.mxf", required = true
    )
    @NotBlank
    val filename: String,

    @Schema(
        description = "The name of the encoding profile to use",
        example = "x264-animated", required = true
    )
    @NotBlank
    val profile: String,

    @Schema(
        description = "A directory path to where the output should be written",
        example = "/an/output/path/dir", required = true
    )
    @NotBlank
    val outputFolder: String,

    @Schema(
        description = "The Creation date for the EncoreJob",
        example = "2021-04-22T03:00:48.759168+02:00", accessMode = Schema.AccessMode.READ_ONLY,
        defaultValue = "now()"
    )
    val createdDate: OffsetDateTime = OffsetDateTime.now(),

    @Schema(
        description = "An url to which the progress status callback should be directed",
        example = "http://projectx/encorecallback", nullable = true
    )
    val progressCallbackUri: URI? = null,

    @Schema(
        description = "The queue priority of the EncoreJob",
        defaultValue = "0", minimum = "0", maximum = "100"
    )
    @Min(0)
    @Max(100)
    val priority: Int = 0,

    @Schema(
        description = "The Display Aspect Ratio to use if the input is anamorphic." +
            " Overrides DAR found from input metadata (for corrupt video metadata)",
        example = "16:9", nullable = true
    )
    @get:Pattern(regexp = AR_REGEX, message = AR_MESSAGE)
    val dar: FractionString? = null,

    @Schema(
        description = "List of FFmpeg filters to apply to all video outputs",
        example = "proxy=filter_path=/ffmpeg-filters/libsvg_filter.so:config='svg=/path/logo-white.svg",
        defaultValue = "[]"
    )
    val globalVideoFilters: List<String> = emptyList(),

    @Schema(
        description = "List of FFmpeg filters to apply to all audio outputs",
        example = "to-do", defaultValue = "[]"
    )
    val globalAudioFilters: List<String> = emptyList(),

    @Schema(
        description = "The exception message, if the EncoreJob failed",
        example = "input/output error", accessMode = Schema.AccessMode.READ_ONLY, nullable = true
    )
    var message: String? = null,

    @Schema(
        description = "The EncoreJob progress",
        example = "57", accessMode = Schema.AccessMode.READ_ONLY, defaultValue = "0"
    )
    var progress: Int = 0,

    @Schema(
        description = "The Encoding speed of the job (compared to it's play speed/input duration)",
        example = "0.334", accessMode = Schema.AccessMode.READ_ONLY, nullable = true
    )
    var speed: Double? = null,

    @Schema(
        description = "The time for when the EncoreJob was picked from the queue)",
        example = "2021-04-19T07:20:43.819141+02:00", accessMode = Schema.AccessMode.READ_ONLY, nullable = true
    )
    var startedDate: OffsetDateTime? = null,

    @Schema(
        description = "The time for when the EncoreJob was completed (fail or success)",
        example = "2021-04-19T07:20:43.819141+02:00", accessMode = Schema.AccessMode.READ_ONLY, nullable = true
    )
    var completedDate: OffsetDateTime? = null,

    @Schema(
        description = "Instruct Encore to overlay encoding metadata on the encoded video stream",
        defaultValue = "false"
    )
    val debugOverlay: Boolean = false,

    @Schema(
        description = "Key/Values to append to the MDC log context", defaultValue = "{}"
    )
    val logContext: Map<String, String> = emptyMap(),

    @Schema(
        description = "Crop output video to given aspect ratio",
        example = "1:1", nullable = true
    )
    @get:Pattern(regexp = AR_REGEX, message = AR_MESSAGE)
    val cropTo: FractionString? = null,

    @Schema(
        description = "Pad output video to given aspect ratio",
        example = "16:9", nullable = true
    )
    @get:Pattern(regexp = AR_REGEX, message = AR_MESSAGE)
    val padTo: FractionString? = null,

    @Schema(
        description = "Seek in video stream to given start time in ms (end time must currently be given)",
        example = "10000", nullable = true
    )
    @PositiveOrZero
    val startTime: Int? = null,

    @Schema(
        description = "Seek in video stream to given end time in ms (start time must currently be given)",
        example = "20000", nullable = true
    )
    @Positive
    val endTime: Int? = null,

    @Schema(
        description = "Time in ms for when the thumbnail should be picked. Overrides profile configuration for thumbnails",
        example = "5000", nullable = true
    )
    @PositiveOrZero
    val thumbnailTime: Int? = null,

    @Schema(
        description = "Only the audio input streams up to the given value",
        example = "2", nullable = true
    )
    @Positive
    val useFirstAudioStreams: Int? = null
) {

    @Schema(
        description = "Analyzed model of the input file",
        accessMode = Schema.AccessMode.READ_ONLY,
        nullable = true
    )
    var input: VideoFile? = null

    @Schema(
        description = "Analyzed models of the output files",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    var output = emptyList<MediaFile>()

    val inputOrThrow: VideoFile
        @JsonIgnore
        get() = input ?: throw RuntimeException("Missing media information for $filename!")

    @Schema(
        description = "The Job Status",
        accessMode = Schema.AccessMode.READ_ONLY
    )
    @Indexed
    var status = Status.NEW
        set(value) {
            field = value
            if (value.isCompleted) {
                completedDate = OffsetDateTime.now()
            }
            if (value == Status.IN_PROGRESS) {
                startedDate = OffsetDateTime.now()
            }
        }

    val contextMap: Map<String, String>
        @JsonIgnore
        get() = mapOf(
            "id" to id.toString(),
            "file" to filename,
            "externalId" to (externalId ?: ""),
            "profile" to profile
        ) + logContext
}
