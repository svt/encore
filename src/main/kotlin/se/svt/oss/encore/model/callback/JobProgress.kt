// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model.callback

import java.util.UUID
import se.svt.oss.encore.model.Status

data class JobProgress(
    val jobId: UUID,
    val externalId: String?,
    val progress: Int,
    val status: Status
)
