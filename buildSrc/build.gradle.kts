plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.25")
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.25")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.5.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.5")
    implementation("org.jmailen.gradle:kotlinter-gradle:4.4.1")
    implementation("pl.allegro.tech.build:axion-release-plugin:1.18.8")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.11.0")
    implementation("com.github.fhermansson:assertj-gradle-plugin:1.1.5")
    implementation("com.github.ben-manes:gradle-versions-plugin:0.51.0")
}
