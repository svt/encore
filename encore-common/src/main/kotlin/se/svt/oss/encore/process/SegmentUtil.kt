// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.mediaanalyzer.file.MediaContainer
import kotlin.math.ceil

fun EncoreJob.segmentLengthOrThrow() = segmentLength ?: throw RuntimeException("No segmentLength in job!")

fun EncoreJob.numSegments(): Int {
    val segLen = segmentLengthOrThrow()
    val readDuration = duration
    return if (readDuration != null) {
        ceil(readDuration / segLen).toInt()
    } else {
        val segments =
            inputs.map { ceil(((it.analyzed as MediaContainer).duration - (it.seekTo ?: 0.0)) / segLen).toInt() }.toSet()
        if (segments.size > 1) {
            throw RuntimeException("Inputs differ in length")
        }
        segments.first()
    }
}

fun EncoreJob.segmentDuration(segmentNumber: Int): Double = when {
    duration == null -> segmentLengthOrThrow()
    segmentNumber < numSegments() - 1 -> segmentLengthOrThrow()
    else -> duration!! % segmentLengthOrThrow()
}

fun EncoreJob.baseName(segmentNumber: Int) = "${baseName}_%05d".format(segmentNumber)
