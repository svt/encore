package se.svt.oss.encore.model.queue

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import java.time.LocalDateTime

internal class QueueItemTest {

    @Test
    fun testSortOrder() {
        val newHighPrioItem = QueueItem("new-high-prio", 100)
        val now = LocalDateTime.now()
        val oldHighPrioItem = QueueItem("old-high-prio", 100, now.minusHours(1))
        val olderHighPrioItem = QueueItem("older-high-prio", 100, now.minusHours(2))
        val segmentOne = QueueItem("segmented", 99, now, 1)
        val segmentTwo = QueueItem("segmented", 99, now, 2)
        val newLowPrioItem = QueueItem("new-low-prio", 10)
        val oldLowPrioItem = QueueItem("old-low-prio", 10, now.minusHours(1))
        val olderLowPrioItem = QueueItem("older-low-prio", 10, now.minusHours(2))

        val expectedSorted = listOf(
            olderHighPrioItem,
            olderHighPrioItem,
            oldHighPrioItem,
            newHighPrioItem,
            segmentOne,
            segmentTwo,
            olderLowPrioItem,
            oldLowPrioItem,
            newLowPrioItem
        )

        assertThat(
            listOf(
                newHighPrioItem,
                olderHighPrioItem,
                oldHighPrioItem,
                segmentOne,
                segmentTwo,
                olderLowPrioItem,
                olderHighPrioItem,
                newLowPrioItem,
                oldLowPrioItem,
            )
                .shuffled()
                .sorted()
        ).isEqualTo(expectedSorted)
    }
}
