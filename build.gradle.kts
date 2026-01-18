plugins {
    java
    id("io.freefair.lombok") version "8.4" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
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

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()
            // Override Google style: keep braces on same line
            custom("braceStyle") {
                it.replace(Regex("""\n\s*\{"""), " {")
            }
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    dependencies {
        // Lombok for reducing boilerplate
        val lombok = "org.projectlombok:lombok:1.18.30"
        "compileOnly"(lombok)
        "annotationProcessor"(lombok)
        "testCompileOnly"(lombok)
        "testAnnotationProcessor"(lombok)

        // Logging
        implementation("org.slf4j:slf4j-api:2.0.9")
        runtimeOnly("ch.qos.logback:logback-classic:1.4.14")

        // Testing - JUnit 5
        testImplementation(platform("org.junit:junit-bom:5.10.1"))
        testImplementation("org.junit.jupiter:junit-jupiter-api")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher") // For VSCode test runner

        // Mockito
        testImplementation("org.mockito:mockito-core:5.8.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")

        // AssertJ
        testImplementation("org.assertj:assertj-core:3.24.2")
    }

    tasks.test {
        useJUnitPlatform()
    }
}
