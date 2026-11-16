plugins {
    id("org.springframework.boot") version "3.5.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "dev.amorphic"

    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Shared golden vectors for contract tests.
        systemProperty("golden.vectors", rootProject.file("amorphic-demo-env/golden/vectors.json").absolutePath)
    }
}

// Dependencies go one way: the demo may depend on the framework, never the reverse.
// A framework project is any project whose directory is under amorphic-systems/.
val frameworkDir = rootProject.file("amorphic-systems").toPath()
fun isFramework(p: Project) = p.projectDir.toPath().startsWith(frameworkDir)

val frameworkDependencyViolations = provider {
    subprojects.filter(::isFramework).flatMap { p ->
        p.configurations.flatMap { c ->
            c.dependencies.withType<ProjectDependency>()
                .map { rootProject.project(it.path) }
                .filterNot(::isFramework)
                .map { "${p.path} depends on demo project ${it.path} (configuration '${c.name}')" }
        }
    }.distinct().sorted()
}

val checkFrameworkDependencies by tasks.registering {
    group = "verification"
    description = "Fails if a project under amorphic-systems/ depends on a project outside it."
    val violations = frameworkDependencyViolations
    doLast {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Framework projects (under amorphic-systems/) must not depend on demo projects:\n" +
                    found.joinToString("\n") { "  - $it" })
        }
        logger.lifecycle("Framework dependencies OK: nothing under amorphic-systems/ depends on the demo.")
    }
}

subprojects {
    if (isFramework(this)) {
        tasks.named("check") { dependsOn(checkFrameworkDependencies) }
    }
}
