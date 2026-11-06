plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

version = "2.1"

dependencies {
    implementation(project(":pricing-calc-core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(testFixtures(project(":pricing-calc-core")))
}
