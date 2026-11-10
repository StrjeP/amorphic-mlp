plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "amorphic-mlp"

include("pricing-calc-core", "pricing-calc-service", "amorphic-demo-app", "inventory-core", "inventory-service",
    "governor-core", "governor-service")
project(":pricing-calc-core").projectDir = file("pricing-calc-java/pricing-calc-core")
project(":pricing-calc-service").projectDir = file("pricing-calc-java/pricing-calc-service")
project(":amorphic-demo-app").projectDir = file("amorphic-demo-app")
project(":inventory-core").projectDir = file("amorphic-inventory/inventory-core")
project(":governor-core").projectDir = file("amorphic-governor/governor-core")
project(":inventory-service").projectDir = file("amorphic-inventory/inventory-service")
project(":governor-service").projectDir = file("amorphic-governor/governor-service")
