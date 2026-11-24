plugins {
    `java-library`
}

// Composition (spec 04): an outcome in, a plan of addressed steps out. A recipe says how to reach an outcome;
// the composer applies it to one request, chooses every step's route once, invokes each step and records the
// plan's topology.
version = "0.1.0"

dependencies {
    api(project(":addressing"))
    api(project(":envelope"))
    api(project(":access"))
    api(project(":topology"))
    api("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
