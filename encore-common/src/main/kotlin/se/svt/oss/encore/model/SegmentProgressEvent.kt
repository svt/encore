// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
data class SegmentProgressEvent(val jobId: UUID, val segment: Int, val success: Boolean)
