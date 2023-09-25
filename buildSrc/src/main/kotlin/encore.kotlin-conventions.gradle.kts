plugins {
    idea
    jacoco
    kotlin("jvm")
    kotlin("plugin.spring")
    id("pl.allegro.tech.build.axion-release")
    id("com.github.fhermansson.assertj-generator")
    id("org.jmailen.kotlinter")
    id("se.ascp.gradle.gradle-versions-filter")
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
    jvmToolchain(17)
}
tasks.lintKotlinTest {
    source = (source - fileTree("src/test/generated-java")).asFileTree
}
tasks.formatKotlinTest {
    source = (source - fileTree("src/test/generated-java")).asFileTree
}
kotlinter {
    disabledRules = arrayOf(
        "import-ordering",
        "trailing-comma-on-declaration-site",
        "trailing-comma-on-call-site"
    )
}
assertjGenerator {
    classOrPackageNames = arrayOf(
        "se.svt.oss.encore.model",
        "se.svt.oss.mediaanalyzer.file"
    )
    entryPointPackage = "se.svt.oss.encore"
    useJakartaAnnotations = true
}

val redissonVersion = "3.23.2"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.10"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.1.3"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2022.0.4"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("org.redisson:redisson-spring-boot-starter:$redissonVersion")
    implementation("org.redisson:redisson-spring-data-31:$redissonVersion") // match boot version
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.assertj:assertj-core")
    testImplementation("io.mockk:mockk:1.13.7")
}



