package se.svt.oss.encore.service.audiomix

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import se.svt.oss.encore.config.EncodingProperties
import se.svt.oss.encore.config.EncoreProperties
import java.io.IOException

class AudioMixPresetServiceTest {

    private lateinit var mixService: AudiomixPresetService
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val encoreProperties =
        EncoreProperties(encoding = EncodingProperties(ClassPathResource("audiomixpreset/audio-mix-presets.yml")))

    @BeforeEach
    internal fun setUp() {
        mixService = AudiomixPresetService(
            objectMapper,
            encoreProperties,
        )
    }

    @Test
    fun `successfully parses existing and valid presets`() {
        val presets = mixService.getAudioMixPresets()
        assertThat(presets).hasSize(2)
        assertThat(presets["default"]).isNotNull
        assertThat(presets["de"]).isNotNull
    }

    @Test
    fun `nonexistent preset throws error`() {
        mixService = AudiomixPresetService(
            objectMapper,
            encoreProperties.copy(
                encoding = encoreProperties.encoding.copy(
                    audioMixPresetLocation = ClassPathResource(
                        "i-dont-exist",
                    ),
                ),
            ),
        )
        assertThatThrownBy {
            mixService.getAudioMixPresets()
        }
            .isInstanceOf(IOException::class.java)
            .hasMessageStartingWith("class path resource [i-dont-exist] cannot be opened because it does not exist")
    }
}
