plugins {
    `java-library`
}

// Uniform access (spec 02): one invoke(address, envelope) -> envelope, whatever the binding:
// local (in-process), remote (a service that speaks the envelope) or adapter (one that doesn't).
version = "0.1.0"

dependencies {
    api(project(":addressing"))
    api(project(":envelope"))
    testImplementation("org.assertj:assertj-core:3.27.3")
}
