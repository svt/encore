// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.service.queue.QueueUtil.getQueueNumberByPriority

internal class QueueUtilTest {

    @Test
    fun getQueueNumberByPriorityConcurrency1() {
        (0..100).forEach {
            assertThat(getQueueNumberByPriority(1, it))
                .`as`("queue for $it, concurrency: 1").isEqualTo(0)
        }
    }

    @Test
    fun getQueueNumberByPriorityConcurrency2() {
        (0..49).forEach {
            assertThat(getQueueNumberByPriority(2, it))
                .`as`("queue for $it, concurrency: 2").isEqualTo(1)
        }
        (50..100).forEach {
            assertThat(getQueueNumberByPriority(2, it))
                .`as`("queue for $it, concurrency: 2").isEqualTo(0)
        }
    }

    @Test
    fun getQueueNumberByPriorityConcurrency3() {
        (0..33).forEach {
            assertThat(getQueueNumberByPriority(3, it))
                .`as`("queue for $it, concurrency: 3").isEqualTo(2)
        }
        (34..66).forEach {
            assertThat(getQueueNumberByPriority(3, it))
                .`as`("queue for $it, concurrency: 3").isEqualTo(1)
        }
        (67..100).forEach {
            assertThat(getQueueNumberByPriority(3, it))
                .`as`("queue for $it, concurrency: 3").isEqualTo(0)
        }
    }
}
