plugins {
    id("encore.kotlin-conventions")
}

dependencies {

    api("se.svt.oss:media-analyzer:2.0.3")
    implementation(kotlin("reflect"))

    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-api:2.2.0")
    compileOnly("org.springframework.data:spring-data-rest-core")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.7.3")

    testImplementation(project(":encore-web"))
    testImplementation("se.svt.oss:junit5-redis-extension:3.0.0")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-data-rest")
}


val integrationTestsPreReq = setOf("mediainfo", "ffmpeg", "ffprobe").map {

    tasks.create("Verify $it is in path, required for integration tests", Exec::class.java) {
        isIgnoreExitValue = true
        executable = it

        if (!it.equals("mediainfo")) {
            args("-hide_banner")
        }
    }
}

tasks.test {
    dependsOn(integrationTestsPreReq)
}

