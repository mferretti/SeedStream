import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    java
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.dependency.check) apply false
    alias(libs.plugins.sonarqube) apply false
}

// Apply SonarQube only when a host is configured (via ~/.gradle/gradle.properties,
// project gradle.properties, -Psonar.host.url=..., or SONAR_HOST_URL env var).
// Without it, ./gradlew sonar is intentionally absent — no noise for devs not running
// a Sonar instance. See docs/QUALITY.md for local setup.
val sonarHost: String? =
    (project.findProperty("sonar.host.url") as String?)
        ?: System.getProperty("sonar.host.url")
        ?: System.getenv("SONAR_HOST_URL")

if (sonarHost != null) {
    apply(plugin = "org.sonarqube")
    val sonarToken: String? =
        (project.findProperty("sonar.token") as String?)
            ?: System.getProperty("sonar.token")
            ?: System.getenv("SONAR_TOKEN")
    val sonarProjectKey =
        (project.findProperty("sonar.projectKey") as String?) ?: "seedstream"
    val sonarProjectName =
        (project.findProperty("sonar.projectName") as String?) ?: "Seedstream"
    extensions.configure<org.sonarqube.gradle.SonarExtension> {
        properties {
            property("sonar.host.url", sonarHost)
            property("sonar.projectKey", sonarProjectKey)
            property("sonar.projectName", sonarProjectName)
            if (sonarToken != null) {
                property("sonar.token", sonarToken)
            }
        }
    }
}

allprojects {
    group = "com.datagenerator"
    version = "0.6.0"
    description = "High-performance test data generator for enterprise applications"

    repositories {
        mavenCentral()
    }
}

// Capture catalog refs at root scope (libs is not accessible inside subprojects {})
val slf4jApiDep = libs.slf4j.api
val logbackClassicDep = libs.logback.classic

// Custom task to run dependency-check on all subprojects
// Note: dependencyCheckAggregate doesn't scan Gradle dependencies properly
// We use dependencyCheckAnalyze on each module instead
tasks.register("dependencyCheckAll") {
    group = "verification"
    description = "Runs OWASP Dependency-Check on all subprojects"
    dependsOn(subprojects.map { it.tasks.named("dependencyCheckAnalyze") })
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.freefair.lombok")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "jacoco")
    apply(plugin = "com.github.spotbugs")
    apply(plugin = "org.owasp.dependencycheck")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            // Apply Apache 2.0 license header
            licenseHeaderFile(rootProject.file("config/license-header.txt"))
            googleJavaFormat()
            // Keep braces on same line (override Google style)
            replaceRegex("Move opening brace to same line", """\n(\s*)\{""", " {")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // Force versions across ALL configurations (including those scanned by OWASP).
    // constraints { implementation(...) } only covers implementation-derived configs —
    // compileClasspath and other configs may still resolve older transitive versions,
    // which OWASP DC flags even when runtimeClasspath overrides them correctly.
    configurations.all {
        resolutionStrategy.force(
            "org.apache.logging.log4j:log4j-core:2.26.0",
            "org.apache.logging.log4j:log4j-api:2.26.0",
            // commons-lang3: compileClasspath resolves 3.14.0 (from AWS SDK transitive)
            // while runtimeClasspath correctly overrides to 3.20.0. Force 3.20.0 everywhere
            // so OWASP DC never sees 3.14.0 on any configuration.
            "org.apache.commons:commons-lang3:3.20.0"
        )
    }

    dependencies {
        // Lombok for reducing boilerplate
        val lombok = "org.projectlombok:lombok:1.18.46"
        "compileOnly"(lombok)
        "annotationProcessor"(lombok)
        "testCompileOnly"(lombok)
        "testAnnotationProcessor"(lombok)

        // Force newer versions to address security vulnerabilities
        constraints {
            implementation("com.google.protobuf:protobuf-java:4.35.1") // CVE-2024-7254
        }

        // Logging
        implementation(slf4jApiDep)
        runtimeOnly(logbackClassicDep)

        // Testing - JUnit 5 (skip for benchmarks module)
        if (project.name != "benchmarks") {
            testImplementation(platform("org.junit:junit-bom:6.1.0"))
            testImplementation("org.junit.jupiter:junit-jupiter-api")
            testImplementation("org.junit.jupiter:junit-jupiter-params")
            testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
            testRuntimeOnly("org.junit.platform:junit-platform-launcher") // For VSCode test runner

            // Mockito
            testImplementation("org.mockito:mockito-core:5.21.0")
            testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")

            // AssertJ
            testImplementation("org.assertj:assertj-core:3.27.7")

            // Testcontainers for integration tests (latest stable)
            testImplementation("org.testcontainers:testcontainers:1.21.4")
            testImplementation("org.testcontainers:junit-jupiter:1.21.4")
            testImplementation("org.testcontainers:kafka:1.21.4")
            testImplementation("org.testcontainers:postgresql:1.21.4")

            // Awaitility for async testing
            testImplementation("org.awaitility:awaitility:4.3.0")
        }
    }

    tasks.test {
        useJUnitPlatform {
            excludeTags("integration")
        }
        finalizedBy("jacocoTestReport")
    }

    // Separate integration test task (excludes slow tests — use slowTest for those)
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests with Testcontainers (excludes @Tag(\"slow\"))"
        group = "verification"

        // Use the same test sources and classes as the test task
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath

        useJUnitPlatform {
            includeTags("integration")
            excludeTags("slow")
        }

        shouldRunAfter(tasks.test)

        // Integration tests may take longer
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        // Set Docker API version for Testcontainers compatibility with newer Docker versions
        environment("DOCKER_API_VERSION", "1.41")
    }

    // Slow integration tests (e.g. Schema Registry with ~500MB Docker image)
    tasks.register<Test>("slowTest") {
        description = "Runs slow integration tests tagged with @Tag(\"slow\")"
        group = "verification"

        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath

        useJUnitPlatform {
            includeTags("slow")
        }

        shouldRunAfter(tasks.named("integrationTest"))

        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }

        environment("DOCKER_API_VERSION", "1.41")
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.test)
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        violationRules {
            rule {
                limit {
                    minimum = "0.70".toBigDecimal()
                }
            }
        }
    }

    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
        excludeFilter.set(file("$rootDir/config/spotbugs-exclude.xml"))
        ignoreFailures.set(false)
    }

    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        reports.create("html") {
            required.set(true)
        }
    }

    configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
        analyzers.assemblyEnabled = false
        failBuildOnCVSS = 7.0f
        suppressionFile = "$rootDir/config/dependency-check-suppressions.xml"

        // Do NOT update NVD during analysis. The OWASP plugin treats an NVD
        // update failure (e.g. a 503) as a fatal error that bypasses
        // failOnError, so an autoUpdate during analyze turns every NVD outage
        // into a failed gate. Instead CI runs `dependencyCheckUpdate` as a
        // separate best-effort step and analysis here scans the cached database.
        autoUpdate = false

        // Optional: Use NVD_API_KEY for faster updates (50 req/30s vs 5 req/30s)
        // Get free key at: https://nvd.nist.gov/developers/request-an-api-key
        nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""

        // Treat cached NVD data as fresh for 24h so a transient NVD outage
        // (recurring 503 windows) does not hard-fail the gate on every run.
        // Combined with the CI NVD cache, a warm cache survives short outages.
        nvd.validForHours = 24

        // Do not fail the build on NVD update/transport errors (e.g. 503): fall
        // back to the cached NVD data and still run the analysis. This keeps the
        // real security gate working during NVD outages. The CVSS gate
        // (failBuildOnCVSS, above) is independent of failOnError, so genuine
        // vulnerabilities still fail the build. CI additionally guards against a
        // cold/empty cache (see security.yml "Verify NVD database") so a missing
        // database fails loudly instead of passing without scanning real data.
        failOnError = false
    }

    // SonarQube: explicitly categorise production vs test sources per module.
    // Without an explicit sonar.tests the scanner treats every src/test/java file as
    // production code with zero coverage, which sinks new-code coverage. Mapping each
    // module's main sourceSet to sonar.sources and test sourceSet to sonar.tests keeps
    // coverage measured against production code only. Modules with no production Java
    // (e.g. benchmarks: JMH-only) contribute no sources and are excluded from coverage.
    if (sonarHost != null) {
        apply(plugin = "org.sonarqube")
        extensions.configure<org.sonarqube.gradle.SonarExtension> {
            properties {
                val mainDirs = sourceSets["main"].allSource.srcDirs.filter { it.exists() }
                val testDirs = sourceSets["test"].allSource.srcDirs.filter { it.exists() }
                property("sonar.sources", mainDirs.joinToString(",") { it.absolutePath })
                property("sonar.tests", testDirs.joinToString(",") { it.absolutePath })
            }
        }
    }
}
