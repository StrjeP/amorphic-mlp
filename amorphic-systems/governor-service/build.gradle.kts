plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// The governor as its own service: governor-core behind HTTP, for a `remote` binding in the address table.
version = "0.1.0"

dependencies {
    implementation(project(":governor-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
