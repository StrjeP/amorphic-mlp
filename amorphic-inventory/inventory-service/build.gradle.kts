plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// The inventory as its own service: inventory-core behind HTTP, for a `remote` binding in the address table.
version = "0.1.0"

dependencies {
    implementation(project(":inventory-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
