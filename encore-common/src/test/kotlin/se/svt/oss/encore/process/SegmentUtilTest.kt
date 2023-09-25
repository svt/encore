// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.defaultVideoFile
import se.svt.oss.encore.longVideoFile
import se.svt.oss.encore.model.input.AudioVideoInput

class SegmentUtilTest {

    private val job = defaultEncoreJob().copy(
        baseName = "segment_test",
        segmentLength = 19.2,
        duration = null,
        inputs = listOf(AudioVideoInput(uri = "test", analyzed = longVideoFile))
    )

    @Test
    fun baseName() {
        assertThat(job.baseName(2)).isEqualTo("segment_test_00002")
    }

    @Test
    fun missingSegmentLength() {
        val encoreJob = job.copy(segmentLength = null)
        val message = "No segmentLength in job!"
        assertThatThrownBy {
            encoreJob.segmentLengthOrThrow()
        }.hasMessage(message)
        assertThatThrownBy {
            encoreJob.numSegments()
        }.hasMessage(message)
        assertThatThrownBy {
            encoreJob.segmentDuration(1)
        }.hasMessage(message)
    }

    @Test
    fun hasSegmentLength() {
        assertThat(job.segmentLengthOrThrow()).isEqualTo(19.2)
    }

    @Test
    fun numSegmentsDurationSet() {
        val encoreJob = job.copy(duration = 125.0)
        assertThat(encoreJob.numSegments()).isEqualTo(7)
    }

    @Test
    fun numSegmentsDurationNotSet() {
        assertThat(job.numSegments()).isEqualTo(141)
    }

    @Test
    fun numSegmentsInputsDiffer() {
        val encoreJob = job.copy(inputs = job.inputs + AudioVideoInput(uri = "test", analyzed = defaultVideoFile))
        assertThatThrownBy { encoreJob.numSegments() }
            .hasMessage("Inputs differ in length")
    }

    @Test
    fun segmentDurationDurationNotSet() {
        assertThat(job.segmentDuration(140)).isEqualTo(19.2)
    }

    @Test
    fun segmentDurationDurationSetFirst() {
        assertThat(job.copy(duration = 125.0).segmentDuration(0)).isEqualTo(19.2)
    }

    @Test
    fun segmentDurationDurationSetLast() {
        assertThat(job.copy(duration = 125.0).segmentDuration(6)).isCloseTo(9.8, Offset.offset(0.001))
    }
}
