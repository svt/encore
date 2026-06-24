plugins {
    id("encore.kotlin-conventions")
    `java-test-fixtures`
}

dependencies {

    api("se.svt.oss:media-analyzer:2.0.11")
    implementation(kotlin("reflect"))

    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.3")
    implementation("org.springframework.data:spring-data-commons")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")

    testImplementation(project(":encore-web"))
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.wiremock:wiremock-standalone:3.12.1")
    testImplementation("org.springframework.boot:spring-boot-starter-data-rest")
    testFixturesImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.6"))
    testFixturesImplementation("com.redis:testcontainers-redis:2.2.4")
    testFixturesImplementation("io.github.oshai:kotlin-logging-jvm:7.0.14")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
    testFixturesRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
