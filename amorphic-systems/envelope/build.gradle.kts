plugins {
    `java-library`
}

// The envelope (spec 02): a header (id, purpose) plus named aspects, each naming its own schema.
// Read and written as JSON, with addresses as canonical text.
version = "0.1.0"

dependencies {
    api(project(":addressing"))
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
