import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

repositories {
    mavenCentral()
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
    group = "com"
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