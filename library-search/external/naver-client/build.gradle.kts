tasks.getByName<Jar>("jar") {
    enabled = true
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
}