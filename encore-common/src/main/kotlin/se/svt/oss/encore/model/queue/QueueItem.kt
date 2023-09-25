package se.svt.oss.encore.model.queue

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
data class QueueItem(
    val id: String,
    val priority: Int = 0,
    val created: LocalDateTime = LocalDateTime.now(),
    val segment: Int? = null
) :
    Comparable<QueueItem> {
    override fun compareTo(other: QueueItem): Int {
        if (this == other) {
            return 0
        }
        val priorityCompare = other.priority.compareTo(priority)
        if (priorityCompare != 0) {
            return priorityCompare
        }
        val createdCompare = created.compareTo(other.created)
        if (createdCompare != 0) {
            return createdCompare
        }
        val idCompare = id.compareTo(other.id)
        if (idCompare != 0) {
            return idCompare
        }
        return (segment ?: 0).compareTo((other.segment ?: 0))
    }
}
