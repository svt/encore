package se.svt.oss.encore.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FfmpegExecutorTest {
    @Nested
    inner class TestGetProgress {
        @Test
        fun `valid time and duration, returns progress`() {
            val logLine = "frame=  240 fps= 24 q=28.0 size=    1024kB time=00:00:10.00 bitrate= 838.9kbits/s speed=1.00x"
            val duration = 20.0 // seconds
            val progress = getProgress(duration, logLine)
            assertNotNull(progress)
            assertEquals(50, progress)
        }

        @Test
        fun `invalid logline, returns null`() {
            val logLine = "RANDOM LOG LINE"
            val duration = 20.0 // seconds
            val progress = getProgress(duration, logLine)
            assertNull(progress)
        }

        @Test
        fun `null duration, returns null`() {
            val logLine =
                "frame=  240 fps= 24 q=28.0 size=    1024kB time=00:00:10.00 bitrate= 838.9kbits/s speed=1.00x"
            val duration: Double? = null
            val progress = getProgress(duration, logLine)
            assertNull(progress)
        }
    }
}
