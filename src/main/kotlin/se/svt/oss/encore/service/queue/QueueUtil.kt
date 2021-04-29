// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2
package se.svt.oss.encore.service.queue

import kotlin.math.max
import kotlin.math.min

object QueueUtil {

    fun getQueueNumberByPriority(concurrency: Int, priority: Int): Int {
        val maxQueueNo = concurrency - 1
        var queueNo = maxQueueNo - (priority.toDouble() / 100 * concurrency).toInt()
        queueNo = min(queueNo, maxQueueNo)
        return max(queueNo, 0)
    }
}
