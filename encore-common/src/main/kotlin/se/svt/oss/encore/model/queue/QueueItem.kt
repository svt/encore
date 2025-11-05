// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.queue

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
@JsonPropertyOrder("created", "id", "segment")
data class QueueItem(
    val id: String,
    val priority: Int = 0,
    val created: LocalDateTime = LocalDateTime.now(),
    val task: Task? = null,
)

enum class TaskType {
    AUDIOVIDEOSEGMENT,
    VIDEOSEGMENT,
    AUDIOFULL,
    AUDIOSEGMENT,
}

data class Task(
    val type: TaskType,
    val taskNo: Int,
    val segment: Int,
)
