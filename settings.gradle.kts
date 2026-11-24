plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "amorphic-mlp"

include("pricing-calc-core", "pricing-calc-service", "amorphic-demo-app", "addressing", "envelope", "access",
    "inventory-core", "inventory-service", "governor-core", "governor-service", "topology")
project(":pricing-calc-core").projectDir = file("pricing-calc-java/pricing-calc-core")
project(":pricing-calc-service").projectDir = file("pricing-calc-java/pricing-calc-service")
project(":amorphic-demo-app").projectDir = file("amorphic-demo-app")
project(":addressing").projectDir = file("amorphic-systems/addressing")
project(":envelope").projectDir = file("amorphic-systems/envelope")
project(":access").projectDir = file("amorphic-systems/access")
project(":inventory-core").projectDir = file("amorphic-systems/inventory-core")
project(":governor-core").projectDir = file("amorphic-systems/governor-core")
project(":inventory-service").projectDir = file("amorphic-systems/inventory-service")
project(":governor-service").projectDir = file("amorphic-systems/governor-service")
project(":topology").projectDir = file("amorphic-systems/topology")
