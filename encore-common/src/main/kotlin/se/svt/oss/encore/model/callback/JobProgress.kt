// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.callback

import se.svt.oss.encore.model.Status
import java.util.UUID

data class JobProgress(
    val jobId: UUID,
    val externalId: String?,
    val progress: Int,
    val status: Status,
)
