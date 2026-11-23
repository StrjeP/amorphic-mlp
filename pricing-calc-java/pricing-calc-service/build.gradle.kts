plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

version = "2.1"

dependencies {
    implementation(project(":pricing-calc-core"))
    // Speaks the envelope natively (POST /invoke), as well as the plain /convert the Python service has.
    implementation(project(":envelope"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(testFixtures(project(":pricing-calc-core")))
}
