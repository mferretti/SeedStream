import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    java
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.spotbugs) apply false
    alias(libs.plugins.dependency.check) apply false
}

allprojects {
    group = "com.datagenerator"
    version = "0.1.0-SNAPSHOT"
    description = "High-performance test data generator for enterprise applications"

    repositories {
        mavenCentral()
    }
}

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

    dependencies {
        // Lombok for reducing boilerplate
        val lombok = "org.projectlombok:lombok:1.18.42"
        "compileOnly"(lombok)
        "annotationProcessor"(lombok)
        "testCompileOnly"(lombok)
        "testAnnotationProcessor"(lombok)

        // Force newer versions to address security vulnerabilities
        constraints {
            implementation("com.google.protobuf:protobuf-java:4.34.0") // CVE-2024-7254
            implementation("com.mysql:mysql-connector-j:9.6.0") // Brings newer protobuf
            implementation("org.apache.logging.log4j:log4j-core:2.26.0") // CVE-2025-68161
            implementation("org.apache.logging.log4j:log4j-api:2.26.0")
        }

        // Logging
        implementation("org.slf4j:slf4j-api:2.0.17")
        runtimeOnly("ch.qos.logback:logback-classic:1.5.32")

        // Testing - JUnit 5 (skip for benchmarks module)
        if (project.name != "benchmarks") {
            testImplementation(platform("org.junit:junit-bom:6.0.3"))
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
            testImplementation("org.testcontainers:mysql:1.21.4")

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

    // Separate integration test task
    tasks.register<Test>("integrationTest") {
        description = "Runs integration tests with Testcontainers"
        group = "verification"
        
        // Use the same test sources and classes as the test task
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        
        useJUnitPlatform {
            includeTags("integration")
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
        
        // Always enable auto-update to download NVD database on first run
        // With NVD_API_KEY, updates are fast (cache handles efficiency)
        autoUpdate = true
        
        // Optional: Use NVD_API_KEY for faster updates (50 req/30s vs 5 req/30s)
        // Get free key at: https://nvd.nist.gov/developers/request-an-api-key
        nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}
