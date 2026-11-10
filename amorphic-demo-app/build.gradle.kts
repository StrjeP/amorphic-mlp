plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

version = "0.1.0"

// Stage 4 fold-in: build with -PfoldIn=true to put pricing-calc-core in-process.
// Without it, core is compileOnly: the app compiles against it but doesn't ship it,
// so a `local` binding fails fast at startup.
val foldIn = providers.gradleProperty("foldIn").map(String::toBoolean).getOrElse(false)

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    // The inventory and governor run in-process, or remotely as their own services, as the
    // address table says. The app carries both: in-process is the default.
    implementation(project(":inventory-core"))
    implementation(project(":governor-core"))
    if (foldIn) {
        implementation(project(":pricing-calc-core"))
    } else {
        compileOnly(project(":pricing-calc-core"))
    }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":pricing-calc-core"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}
