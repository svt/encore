plugins {
    id("encore.kotlin-conventions")
    `java-test-fixtures`
}

dependencies {

    api("se.svt.oss:media-analyzer:2.0.4")
    implementation(kotlin("reflect"))

    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-api:2.2.0")
    compileOnly("org.springframework.data:spring-data-rest-core")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.7.3")

    testImplementation(project(":encore-web"))
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-data-rest")
    testFixturesImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.1.3"))
    testFixturesImplementation("com.redis:testcontainers-redis:2.2.0")
    testFixturesImplementation("io.github.microutils:kotlin-logging:3.0.5")
    testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")
}

