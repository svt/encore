plugins {
    id("encore.kotlin-conventions")
    id("encore.spring-boot-app-conventions")
}

springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":encore-common"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("org.springframework.boot:spring-boot-starter-data-rest")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    testImplementation(testFixtures(project(":encore-common")))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
