import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.fhermansson.gradle.assertj.plugin.GenerateAssertions
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    idea
    jacoco
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.github.fhermansson.assertj-generator")
    id("org.jmailen.kotlinter")
    id("com.github.ben-manes.versions")
    id("io.spring.dependency-management")
}

group = "se.svt.oss"
version = rootProject.version

tasks.withType<Test> {
    useJUnitPlatform()
}
apply(from = "../checks.gradle")

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}
tasks.withType<LintTask> {
    exclude { it.file.path.contains("generated-java") }
    mustRunAfter(tasks.withType<GenerateAssertions>())
}
tasks.withType<FormatTask> {
    exclude { it.file.path.contains("generated-java") }
    mustRunAfter(tasks.withType<GenerateAssertions>())
}

assertjGenerator {
    classOrPackageNames = arrayOf(
        "se.svt.oss.encore.model",
        "se.svt.oss.mediaanalyzer.file"
    )
    entryPointPackage = "se.svt.oss.encore"
    useJakartaAnnotations = true
}
tasks.test {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")
    implementation("io.lettuce:lettuce-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
}
