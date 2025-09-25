import org.springframework.boot.gradle.tasks.bundling.BootJar
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("org.graalvm.buildtools.native")
    id("io.spring.dependency-management")
}

graalvmNative {
    binaries.all {
        buildArgs.add("--strict-image-heap")
    }
}
tasks.named<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.5")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")
}



