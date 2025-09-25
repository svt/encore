// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import org.springframework.validation.annotation.Validated
import se.svt.oss.encore.model.input.Input
import se.svt.oss.mediaanalyzer.file.MediaFile
import java.time.OffsetDateTime
import java.util.UUID

@Validated
@RedisHash("encore-jobs", timeToLive = (60 * 60 * 24 * 7).toLong()) // 1 week ttl
@Tag(name = "encorejob")
data class EncoreJob(

    @field:Schema(
        description = "The Encore Internal EncoreJob Identity",
        example = "fb2baa17-8972-451b-bb1e-1bc773283476",
        readOnly = true,
        hidden = false,
        defaultValue = "A random UUID",
    )
    @Id
    val id: UUID = UUID.randomUUID(),

    @field:Schema(
        description = "External id - for external backreference",
        example = "any-string",
        nullable = true,
    )
    val externalId: String? = null,

    @field:Schema(
        description = "The name of the encoding profile to use",
        example = "x264-animated",
        required = true,
    )
    @field:NotBlank
    val profile: String,

    @field:Schema(
        description = "Properties for evaluation of spring spel expressions in profile",
        defaultValue = "{}",
    )
    val profileParams: Map<String, Any?> = emptyMap(),

    @field:Schema(
        description = "A directory path to where the output should be written",
        example = "/an/output/path/dir",
        required = true,
    )
    @field:NotBlank
    val outputFolder: String,

    @field:Schema(
        description = "Base filename of output files",
        example = "any_file",
        required = true,
    )
    @field:NotBlank
    val baseName: String,

    @field:Schema(
        description = "The Creation date for the EncoreJob",
        example = "2021-04-22T03:00:48.759168+02:00",
        readOnly = true,
        defaultValue = "now()",
    )
    @Indexed
    val createdDate: OffsetDateTime = OffsetDateTime.now(),

    @field:Schema(
        description = "An url to which the progress status callback should be directed",
        example = "http://projectx/encorecallback",
        nullable = true,
    )
    val progressCallbackUri: String? = null,

    @field:Schema(
        description = "The queue priority of the EncoreJob",
        defaultValue = "0",
        minimum = "0",
        maximum = "100",
    )
    @field:Min(0)
    @field:Max(100)
    val priority: Int = 0,

    @field:Schema(
        description = "Transcode segments of specified length in seconds in parallell. Should be a multiple of target GOP.",
        example = "19.2",
        nullable = true,
    )
    @field:Positive
    val segmentLength: Double? = null,

    @field:Schema(
        description = "The exception message, if the EncoreJob failed",
        example = "input/output error",
        readOnly = true,
        nullable = true,
    )
    var message: String? = null,

    @field:Schema(
        description = "The EncoreJob progress",
        example = "57",
        readOnly = true,
        defaultValue = "0",
    )
    var progress: Int = 0,

    @field:Schema(
        description = "The Encoding speed of the job (compared to it's play speed/input duration)",
        example = "0.334",
        readOnly = true,
        nullable = true,
    )
    var speed: Double? = null,

    @field:Schema(
        description = "The time for when the EncoreJob was picked from the queue)",
        example = "2021-04-19T07:20:43.819141+02:00",
        readOnly = true,
        nullable = true,
    )
    var startedDate: OffsetDateTime? = null,

    @field:Schema(
        description = "The time for when the EncoreJob was completed (fail or success)",
        example = "2021-04-19T07:20:43.819141+02:00",
        readOnly = true,
        nullable = true,
    )
    var completedDate: OffsetDateTime? = null,

    @field:Schema(
        description = "Instruct Encore to overlay encoding metadata on the encoded video stream",
        defaultValue = "false",
    )
    val debugOverlay: Boolean = false,

    @field:Schema(
        description = "Key/Values to append to the MDC log context",
        defaultValue = "{}",
    )
    val logContext: Map<String, String> = emptyMap(),

    @field:Schema(description = "Seek to given time in seconds before encoding output.", nullable = true, example = "60.0")
    val seekTo: Double? = null,

    @field:Schema(description = "Limit output to given duration.", nullable = true, example = "60.0")
    val duration: Double? = null,

    @field:Schema(
        description = "Time in seconds for when the thumbnail should be picked. Overrides profile configuration for thumbnails",
        example = "1800.5",
        nullable = true,
    )
    @field:Positive
    val thumbnailTime: Double? = null,

    @field:NotEmpty
    val inputs: List<Input> = emptyList(),
) {

    @Schema(
        description = "Analyzed models of the output files",
        readOnly = true,
    )
    var output = emptyList<MediaFile>()

    @Schema(
        description = "The Job Status",
        readOnly = true,
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
            "file" to baseName,
            "externalId" to (externalId ?: ""),
            "profile" to profile,
        ) + logContext
}
