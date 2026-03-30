import org.gradle.kotlin.dsl.getByName
import org.springframework.boot.gradle.tasks.bundling.BootJar

tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

dependencies {
    implementation(project(":common"))
    implementation(project(":external:naver-client"))
}

