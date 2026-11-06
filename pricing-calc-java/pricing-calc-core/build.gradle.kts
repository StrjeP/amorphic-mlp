plugins {
    `java-library`
    `java-test-fixtures`
}

// v2.0 has the planted HALF_UP bug; v2.1 fixes it to HALF_EVEN.
// Keep in step with PricingCalcCore.VERSION.
version = "2.0"

dependencies {
    // GoldenVectors lives in test fixtures so the service's contract tests can reuse it.
    testFixturesImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}
