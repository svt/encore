import org.springframework.boot.gradle.tasks.bundling.BootJar
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("org.graalvm.buildtools.native")
    id("io.spring.dependency-management")
}

tasks.named<BootJar>("bootJar") {
    archiveClassifier.set("boot")
}
graalvmNative {
    binaries {
        named("main") {
            buildArgs.addAll(
                "-H:+UnlockExperimentalVMOptions",
                "-H:+StaticExecutableWithDynamicLibC",
                "-H:+AddAllCharsets",
                "-J-Dfile.encoding=UTF-8",
            )
        }
    }
}
dependencies {
    implementation("org.springframework.boot:spring-boot-jackson2")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
}
