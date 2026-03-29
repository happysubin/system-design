import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}




subprojects {
    group = "com.library"
    version = "0.0.1-SNAPSHOT"
    description = "library-search"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")


    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-webmvc")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("tools.jackson.module:jackson-module-kotlin")
        testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        testImplementation("io.kotest:kotest-runner-junit5:6.1.0")
        testImplementation("io.kotest:kotest-assertions-core:6.1.0")
        testImplementation("io.kotest:kotest-extensions-spring:6.1.0")

        testImplementation("com.ninja-squad:springmockk:5.0.1")
        testImplementation("io.mockk:mockk:1.14.9")
    }

    val springCloudVersion = "2025.1.0"

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.getByName<BootJar>("bootJar") {
        enabled = false
    }

    tasks.getByName<Jar>("jar") {
        enabled = true
    }
}