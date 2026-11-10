plugins {
    `java-library`
}

// The inventory: an in-memory picture of the running system, built from observation.
// Plain library (no Spring) so it can run in-process in the app or behind its own service
// (inventory-service). RemoteInventory is the client for the service.
version = "0.1.0"

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
