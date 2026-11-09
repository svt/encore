plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.21")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.1.2")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.jmailen.gradle:kotlinter-gradle:5.7.0")
    implementation("pl.allegro.tech.build:axion-release-plugin:1.18.8")
    implementation("org.graalvm.buildtools:native-gradle-plugin:1.1.18")
    implementation("com.github.fhermansson:assertj-gradle-plugin:2.0.1")
    implementation("io.github.ben-manes:gradle-versions-plugin:0.66.0")
}
