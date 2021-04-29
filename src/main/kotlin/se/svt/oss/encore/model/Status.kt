// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model

enum class Status(val isCompleted: Boolean) {
    NEW(false),
    QUEUED(false),
    IN_PROGRESS(false),
    SUCCESSFUL(true),
    FAILED(true),
    CANCELLED(true);

    val isCancelled: Boolean
        get() = this == CANCELLED
}
