// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.process

import org.assertj.core.data.Offset
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import se.svt.oss.encore.Assertions.assertThat
import se.svt.oss.encore.Assertions.assertThatThrownBy
import se.svt.oss.encore.defaultEncoreJob
import se.svt.oss.encore.longVideoFile
import se.svt.oss.encore.model.AudioEncodingMode
import se.svt.oss.encore.model.SegmentedEncodingInfo
import se.svt.oss.encore.model.input.AudioVideoInput
import kotlin.math.ceil

class SegmentUtilTest {

    private val job = defaultEncoreJob().copy(
        baseName = "segment_test",
        duration = null,
        inputs = listOf(AudioVideoInput(uri = "test", analyzed = longVideoFile)),
        segmentedEncodingInfo = SegmentedEncodingInfo(
            segmentLength = 19.2,
            numSegments = ceil(longVideoFile.duration / 19.2).toInt(),
            numTasks = ceil(longVideoFile.duration / 19.2).toInt(),
            audioEncodingMode = AudioEncodingMode.ENCODE_WITH_VIDEO,
            audioSegmentPadding = 0.0,
            audioSegmentLength = 0.0,
            numAudioSegments = 0,
        ),
    )

    @Nested
    inner class SegmentDurationTest {

        @Test
        fun `returns full segment length when duration not set`() {
            assertThat(job.segmentDuration(0)).isEqualTo(19.2)
            assertThat(job.segmentDuration(5)).isEqualTo(19.2)
            assertThat(job.segmentDuration(140)).isEqualTo(19.2)
        }

        @Test
        fun `returns full segment length for non-last segments when duration set`() {
            val jobWithDuration = job.copy(
                duration = 125.0,
                segmentedEncodingInfo = job.segmentedEncodingInfo!!.copy(
                    numSegments = 7,
                ),
            )
            assertThat(jobWithDuration.segmentDuration(0)).isEqualTo(19.2)
            assertThat(jobWithDuration.segmentDuration(3)).isEqualTo(19.2)
            assertThat(jobWithDuration.segmentDuration(5)).isEqualTo(19.2)
        }

        @Test
        fun `returns remainder for last segment when duration set`() {
            val jobWithDuration = job.copy(
                duration = 125.0,
                segmentedEncodingInfo = job.segmentedEncodingInfo!!.copy(
                    numSegments = 7,
                ),
            )
            // 125.0 % 19.2 = 9.8
            assertThat(jobWithDuration.segmentDuration(6)).isCloseTo(9.8, Offset.offset(0.001))
        }

        @Test
        fun `handles exact multiple of segment length`() {
            val jobWithDuration = job.copy(
                duration = 96.0, // Exactly 5 segments of 19.2
                segmentedEncodingInfo = job.segmentedEncodingInfo!!.copy(
                    numSegments = 5,
                ),
            )
            // When duration is an exact multiple of segment length,
            // the last segment should still have the full segment length
            assertThat(jobWithDuration.segmentDuration(4)).isCloseTo(19.2, Offset.offset(0.001))
        }

        @Test
        fun `throws exception for invalid segment number`() {
            val jobWithDuration = job.copy(
                duration = 96.0, // Exactly 5 segments of 19.2
                segmentedEncodingInfo = job.segmentedEncodingInfo!!.copy(
                    numSegments = 5,
                ),
            )

            assertThatThrownBy { jobWithDuration.segmentDuration(5) }
                .hasMessage("segmentNumber 5 is out of range for job with 5 segments")
        }

        @Test
        fun `throws exception when segmentedEncodingInfo is missing`() {
            val jobWithoutInfo = job.copy(segmentedEncodingInfo = null)
            assertThatThrownBy {
                jobWithoutInfo.segmentDuration(0)
            }.hasMessage("No segmentedEncodingInfo in job!")
        }
    }

    @Nested
    inner class BaseNameTest {

        @Test
        fun `generates correct base name with segment number`() {
            assertThat(job.baseName(2)).isEqualTo("segment_test_00002")
        }
    }

    @Nested
    inner class SegmentSuffixFromFilenameTest {

        @Test
        fun `extracts suffix from segment filename`() {
            val encoreJob = job.copy(baseName = "test_video")
            assertThat(encoreJob.segmentSuffixFromFilename("test_video_00003_720p.mp4")).isEqualTo("_720p.mp4")
        }

        @Test
        fun `extracts suffix when no additional suffix present`() {
            val encoreJob = job.copy(baseName = "test_video")
            assertThat(encoreJob.segmentSuffixFromFilename("test_video_00000.mp4")).isEqualTo(".mp4")
        }

        @Test
        fun `throws exception for invalid segment filename`() {
            val encoreJob = job.copy(baseName = "test_video")
            assertThatThrownBy {
                encoreJob.segmentSuffixFromFilename("wrong_name.mp4")
            }.hasMessageContaining("Could not find segment suffix for file wrong_name.mp4")
        }
    }
}
