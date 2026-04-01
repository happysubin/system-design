import org.gradle.kotlin.dsl.getByName
import org.springframework.boot.gradle.tasks.bundling.BootJar

tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

allOpen {
    // JPA 어노테이션
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
}


dependencies {
    implementation(project(":common"))
    implementation(project(":external:naver-client"))
    implementation(project(":external:kakao-client"))
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.2")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

    testImplementation("org.springframework.boot:spring-boot-data-jpa-test")

    runtimeOnly("com.h2database:h2")

    developmentOnly("org.springframework.boot:spring-boot-h2console")
}

