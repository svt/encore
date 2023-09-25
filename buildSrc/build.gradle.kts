plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}
kotlin {
    jvmToolchain(17)
}
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.10"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.1.3"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2022.0.4"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
    implementation("org.jetbrains.kotlin:kotlin-allopen")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:3.1.3")
    implementation("org.jmailen.gradle:kotlinter-gradle:3.13.0")
    implementation("pl.allegro.tech.build:axion-release-plugin:1.14.3")
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.9.25")
    implementation("com.github.fhermansson:assertj-gradle-plugin:1.1.5")
    implementation("se.ascp.gradle:gradle-versions-filter:0.1.16")
}
