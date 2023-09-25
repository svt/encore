import org.springframework.boot.gradle.tasks.bundling.BootJar
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("org.graalvm.buildtools.native")
}

tasks.named<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
}



