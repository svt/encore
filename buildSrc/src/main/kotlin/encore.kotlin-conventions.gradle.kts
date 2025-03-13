import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    jacoco
    kotlin("jvm")
    kotlin("plugin.spring")
    id("pl.allegro.tech.build.axion-release")
    id("com.github.fhermansson.assertj-generator")
    id("org.jmailen.kotlinter")
    id("com.github.ben-manes.versions")
    id("io.spring.dependency-management")
}

group = "se.svt.oss"
project.version = scmVersion.version

tasks.withType<Test> {
    useJUnitPlatform()
}
apply { from("../checks.gradle") }
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
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

tasks.lintKotlinTest {
    source = (source - fileTree("src/test/generated-java")).asFileTree
}
tasks.formatKotlinTest {
    source = (source - fileTree("src/test/generated-java")).asFileTree
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
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.3")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
    }
}
dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.5")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:1.13.17")
}



