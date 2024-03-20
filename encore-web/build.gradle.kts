plugins {
    id("encore.kotlin-conventions")
    id("encore.spring-boot-app-conventions")
}

dependencies {
    implementation(project(":encore-common"))
    implementation("org.springframework.boot:spring-boot-starter-data-rest")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(testFixtures(project(":encore-common")))
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}