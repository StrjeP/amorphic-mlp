plugins {
    `java-library`
}

// Consistent addressing (spec 01): the Address type, versions and ranges, instances, and the resolver.
// Plain Java with no dependencies, so every other framework project can use it.
version = "0.1.0"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.assertj:assertj-core:3.27.3")
}
