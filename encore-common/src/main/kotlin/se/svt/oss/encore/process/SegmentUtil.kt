// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import se.svt.oss.encore.model.EncoreJob

fun EncoreJob.segmentLengthOrThrow() = segmentedEncodingInfoOrThrow().segmentLength

fun EncoreJob.segmentedEncodingInfoOrThrow() = segmentedEncodingInfo ?: throw RuntimeException("No segmentedEncodingInfo in job!")

fun EncoreJob.segmentDuration(segmentNumber: Int): Double {
    val numSegments = segmentedEncodingInfoOrThrow().numSegments
    return when {
        duration == null -> segmentLengthOrThrow()
        segmentNumber < numSegments - 1 -> segmentLengthOrThrow()
        segmentNumber == numSegments - 1 ->
            // This correctly handles the case where the duration is an exact multiple of the segment length
            duration!! - segmentLengthOrThrow() * (numSegments - 1)
        else -> throw IllegalArgumentException("segmentNumber $segmentNumber is out of range for job with $numSegments segments")
    }
}

fun EncoreJob.baseName(segmentNumber: Int) = "${baseName}_%05d".format(segmentNumber)

fun EncoreJob.segmentSuffixFromFilename(file: String): String {
    val regex = Regex("${baseName}_\\d{5}(.*)")
    val match = regex.find(file) ?: throw RuntimeException("Could not find segment suffix for file $file")
    return match.groupValues[1]
}

fun EncoreJob.targetFilenameFromSegmentFilename(segmentFile: String) =
    segmentFile.replace(Regex("^${baseName}_\\d{5}"), baseName)
