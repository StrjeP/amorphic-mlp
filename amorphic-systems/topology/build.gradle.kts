plugins {
    `java-library`
}

// Topology (spec 03): the fixed record of the resources each call used, addressed by a hash of its content,
// and an append-only log of topologies and the calls that ran on them.
version = "0.1.0"

dependencies {
    api(project(":addressing"))
    api(project(":envelope"))
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
