// SPDX-FileCopyrightText: 2025 Eyevinn Technology AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.model

/**
 * Defines how audio should be encoded when using segmented encoding.
 */
enum class AudioEncodingMode {
    /**
     * Encode audio and video together in the same segments.
     * Creates N tasks of type AUDIOVIDEOSEGMENT.
     */
    ENCODE_WITH_VIDEO,

    /**
     * Encode audio separately from video as a single full-length file (not segmented).
     * Creates 1 AUDIOFULL task + N VIDEOSEGMENT tasks.
     */
    ENCODE_SEPARATELY_FULL,

    /**
     * Encode audio separately from video, with both audio and video segmented.
     * Creates N AUDIOSEGMENT tasks + N VIDEOSEGMENT tasks (2N total tasks).
     */
    ENCODE_SEPARATELY_SEGMENTED,
}
