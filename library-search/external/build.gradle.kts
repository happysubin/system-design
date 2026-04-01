
tasks.getByName<Jar>("jar") {
    enabled = true
}

subprojects {
    dependencies {
        implementation(project(":common"))
        api("org.springframework.cloud:spring-cloud-starter-openfeign")
    }
}

