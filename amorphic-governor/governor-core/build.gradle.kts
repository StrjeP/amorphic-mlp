plugins {
    `java-library`
}

// The governor: applies a rule table to the inventory and proposes the next address table.
// Plain library (no Spring) so it can run in-process in the app or behind its own service
// (governor-service). RemoteGovernor is the client for the service.
version = "0.1.0"

dependencies {
    api(project(":inventory-core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
