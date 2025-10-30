package se.svt.oss.encore.service.mediaanalyzer

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import se.svt.oss.mediaanalyzer.MediaAnalyzer
import se.svt.oss.mediaanalyzer.file.MediaFile

class MediaAnalyzerServiceTest {
    private val mediaAnalyzer = mockk<MediaAnalyzer>()

    private val mediaAnalyzerService = MediaAnalyzerService(mediaAnalyzer)

    @Test
    fun testAnalyze() {
        val mediaFile = mockk<MediaFile>()
        val slot = io.mockk.slot<String>()
        every { mediaAnalyzer.analyze(capture(slot)) } returns mediaFile

        val actual = mediaAnalyzerService.analyze("testInput")
        assertEquals(mediaFile, actual)
        assertEquals("testInput", slot.captured)
    }
}
