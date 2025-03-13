// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.test.context.ActiveProfiles
import java.io.File

@ActiveProfiles("test-local")
@WireMockTest
class LocalEncodeIntegrationTest(wireMockRuntimeInfo: WireMockRuntimeInfo) : EncoreIntegrationTestBase(wireMockRuntimeInfo) {

    @Test
    fun jobIsSuccessfulAndNoAudioPresets(@TempDir outputDir: File) {
        successfulTest(
            job(outputDir = outputDir, file = testFileSurround),
            defaultExpectedOutputFiles(outputDir, testFileSurround) + listOf(
                expectedFile(
                    outputDir,
                    testFileSurround,
                    "SURROUND.mp4",
                ),
            ),
        )
    }
}
