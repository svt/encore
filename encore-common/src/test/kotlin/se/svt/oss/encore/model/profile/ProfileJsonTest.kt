package se.svt.oss.encore.model.profile

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import se.svt.oss.encore.model.profile.ProfileAssert.assertThat

class ProfileJsonTest {

    private val yamlMapper = YAMLMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    private val objectMapper = ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @Test
    fun testSerializeProgramToJson() {
        serializeDeserializeJson(readProfile("/profile/program.yml"))
    }

    @Test
    fun testSerializeProgramX265ToJson() {
        serializeDeserializeJson(readProfile("/profile/program-x265.yml"))
    }

    @Test
    fun testSerializeProgramToYaml() {
        serializeDeserializeYaml(readProfile("/profile/program.yml"))
    }

    @Test
    fun testSerializeProgramX265ToYaml() {
        serializeDeserializeYaml(readProfile("/profile/program-x265.yml"))
    }

    private fun serializeDeserializeJson(profile: Profile) {
        val pretty = ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
        println(pretty.writeValueAsString(profile))
        val serialized = objectMapper.writeValueAsString(profile)
        val deserialized: Profile = objectMapper.readValue(serialized, Profile::class.java)
        assertThat(deserialized)
            .isEqualTo(profile)
    }

    private fun serializeDeserializeYaml(profile: Profile) {
        val pretty = YAMLMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
        println(pretty.writeValueAsString(profile))
        val serialized = yamlMapper.writeValueAsString(profile)
        val deserialized: Profile = yamlMapper.readValue(serialized, Profile::class.java)
        assertThat(deserialized)
            .isEqualTo(profile)
    }

    private fun readProfile(path: String): Profile =
        ProfileJsonTest::class.java.getResourceAsStream(path).use {
            yamlMapper.readValue(it)
        }
}
