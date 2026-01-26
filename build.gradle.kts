import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    java
    id("io.freefair.lombok") version "8.4" apply false
    id("com.diffplug.spotless") version "8.1.0" apply false
    id("com.github.spotbugs") version "6.4.8" apply false
    id("org.owasp.dependencycheck") version "9.0.9" apply false
}

allprojects {
    group = "com.datagenerator"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
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

        // Logging
        implementation("org.slf4j:slf4j-api:2.0.9")
        runtimeOnly("ch.qos.logback:logback-classic:1.5.26")

        // Testing - JUnit 5
        testImplementation(platform("org.junit:junit-bom:6.0.2"))
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher") // For VSCode test runner

        // Mockito
        testImplementation("org.mockito:mockito-core:5.21.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.21.0")

        // AssertJ
        testImplementation("org.assertj:assertj-core:3.27.6")
    }

    tasks.test {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport")
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
        ignoreFailures.set(true)
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
        // Skip NVD updates - requires API key since 2023
        // Run manually with: ./gradlew dependencyCheckUpdate dependencyCheckAnalyze
        autoUpdate = false
        skip = System.getenv("CI") == "true"  // Disable in CI
    }
}
