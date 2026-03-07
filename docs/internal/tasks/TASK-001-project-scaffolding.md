# TASK-001: Project Scaffolding and Build Setup

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: None  
**Human Supervision**: LOW (review build configuration)

---

## Objective

Set up the Gradle multi-module project structure with Java 21 toolchain, common dependencies, and code quality tools.

---

## Implementation Details

### 1. Create Root Project Structure

Create the following directory structure:
```
datagenerator/
├── build.gradle.kts          # Root build file
├── settings.gradle.kts       # Module definitions
├── gradle.properties         # Build properties
├── gradlew                   # Gradle wrapper (Unix)
├── gradlew.bat               # Gradle wrapper (Windows)
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── config/                   # Configuration files
│   ├── spotbugs-exclude.xml
│   └── dependency-check-suppressions.xml
└── .github/
    └── workflows/
        └── build.yml         # CI/CD pipeline
```

### 2. Configure Root build.gradle.kts

```kotlin
plugins {
    java
    id("com.diffplug.spotless") version "6.23.3"
    id("org.owasp.dependencycheck") version "9.0.7"
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
    apply(plugin = "com.diffplug.spotless")
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    
    dependencies {
        // Lombok for all modules
        compileOnly("org.projectlombok:lombok:1.18.30")
        annotationProcessor("org.projectlombok:lombok:1.18.30")
        
        // Logging
        implementation("org.slf4j:slf4j-api:2.0.9")
        runtimeOnly("ch.qos.logback:logback-classic:1.4.14")
        
        // Testing
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
        testImplementation("org.mockito:mockito-core:5.8.0")
        testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
        testImplementation("org.assertj:assertj-core:3.24.2")
        testCompileOnly("org.projectlombok:lombok:1.18.30")
        testAnnotationProcessor("org.projectlombok:lombok:1.18.30")
    }
    
    tasks.test {
        useJUnitPlatform()
    }
    
    // Spotless configuration
    spotless {
        java {
            googleJavaFormat("1.18.1")
            toggleOffOn()
            target("src/**/*.java")
            
            // Custom: opening braces on same line
            custom("openingBraces") { content ->
                content.replace(Regex("\\)\\s*\\n\\s*\\{")) { ") {" }
            }
        }
    }
}

// Dependency-Check configuration (root level)
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "config/dependency-check-suppressions.xml"
}
```

### 3. Configure settings.gradle.kts

```kotlin
rootProject.name = "datagenerator"

include(
    "core",
    "schema",
    "generators",
    "formats",
    "destinations",
    "cli"
)
```

### 4. Create Module build.gradle.kts Files

Create `build.gradle.kts` in each module with module-specific dependencies:

**core/build.gradle.kts**:
```kotlin
dependencies {
    // Core has no module dependencies
}
```

**schema/build.gradle.kts**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.0")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("org.glassfish:jakarta.el:4.0.2")
}
```

**generators/build.gradle.kts**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
}
```

**formats/build.gradle.kts**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
}
```

**destinations/build.gradle.kts**:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    
    // Kafka (compileOnly - users provide at runtime)
    compileOnly("org.apache.kafka:kafka-clients:3.6.1")
    
    // Database (compileOnly - users provide at runtime)
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.postgresql:postgresql:42.7.1")
    compileOnly("com.mysql:mysql-connector-j:8.2.0")
}
```

**cli/build.gradle.kts**:
```kotlin
plugins {
    application
}

application {
    mainClass.set("com.datagenerator.cli.Main")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    implementation(project(":destinations"))
    
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
}
```

### 5. Create gradle.properties

```properties
org.gradle.jvmargs=-Xmx2g
org.gradle.parallel=true
org.gradle.caching=true
```

### 6. Create Module Directory Structure

For each module, create:
```
<module>/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── datagenerator/
    │   │           └── <module>/
    │   └── resources/
    └── test/
        ├── java/
        │   └── com/
        │       └── datagenerator/
        │           └── <module>/
        └── resources/
```

### 7. Create SpotBugs Exclusion File

`config/spotbugs-exclude.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter>
    <!-- Exclude Lombok generated code -->
    <Match>
        <Class name="~.*\$.*"/>
    </Match>
    
    <!-- Exclude test utilities -->
    <Match>
        <Package name="~.*\.test\..*"/>
    </Match>
</FindBugsFilter>
```

### 8. Create OWASP Dependency-Check Suppressions

`config/dependency-check-suppressions.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <!-- Add suppressions as needed for false positives -->
</suppressions>
```

### 9. Create GitHub Actions Workflow

`.github/workflows/build.yml`:
```yaml
name: Build and Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'corretto'
        cache: gradle
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Check code formatting
      run: ./gradlew spotlessCheck
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Run tests
      run: ./gradlew test
    
    - name: Upload test results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-results
        path: |
          **/build/test-results/test/*.xml
          **/build/reports/tests/test/
```

---

## Acceptance Criteria

- ✅ Root project builds successfully: `./gradlew build`
- ✅ All modules compile without errors
- ✅ Java 21 toolchain is enforced (verify: `./gradlew -q javaToolchains`)
- ✅ Spotless can format code: `./gradlew spotlessApply`
- ✅ Spotless check passes: `./gradlew spotlessCheck`
- ✅ Module dependencies follow correct order (no circular dependencies)
- ✅ GitHub Actions workflow exists and is valid YAML

---

## Testing Requirements

### Manual Tests

1. **Build Test**:
   ```bash
   ./gradlew clean build
   # Expected: BUILD SUCCESSFUL
   ```

2. **Toolchain Test**:
   ```bash
   ./gradlew -q javaToolchains
   # Expected: Shows Java 21 as detected and in use
   ```

3. **Spotless Test**:
   ```bash
   ./gradlew spotlessCheck
   # Expected: BUILD SUCCESSFUL (no formatting issues)
   ```

4. **Dependency Graph Test**:
   ```bash
   ./gradlew :cli:dependencies --configuration compileClasspath
   # Expected: Shows cli → destinations → formats → generators → schema → core
   # No circular dependencies
   ```

### Automated Tests

No unit tests required for this task (infrastructure setup).

---

## Files Created

- `build.gradle.kts` (root)
- `settings.gradle.kts`
- `gradle.properties`
- `core/build.gradle.kts`
- `schema/build.gradle.kts`
- `generators/build.gradle.kts`
- `formats/build.gradle.kts`
- `destinations/build.gradle.kts`
- `cli/build.gradle.kts`
- `config/spotbugs-exclude.xml`
- `config/dependency-check-suppressions.xml`
- `.github/workflows/build.yml`
- Module directory structures (src/main/java, src/test/java, etc.)

---

## Common Issues & Solutions

**Issue**: Java 21 not found  
**Solution**: Install Java 21 using SDKMAN: `sdk install java 21.0.9-amzn`

**Issue**: Gradle wrapper not executable  
**Solution**: `chmod +x gradlew`

**Issue**: Circular dependency error  
**Solution**: Check `settings.gradle.kts` and module `build.gradle.kts` dependencies. Follow the correct order: cli → destinations → formats → generators → schema → core

**Issue**: Spotless fails on existing code  
**Solution**: Run `./gradlew spotlessApply` to auto-format, then commit changes

---

## Completion Checklist

- [x] Root build.gradle.kts created with all plugins
- [x] settings.gradle.kts includes all 6 modules
- [x] All module build.gradle.kts files created
- [x] Module dependencies follow correct order
- [x] Java 21 toolchain configured
- [x] Lombok configured for all modules
- [x] Testing dependencies added (JUnit 5, Mockito, AssertJ)
- [x] Spotless configured with Google Java Style
- [x] SpotBugs exclusions configured
- [x] OWASP Dependency-Check configured
- [x] GitHub Actions workflow created
- [x] Build passes: `./gradlew build`
- [x] Formatting check passes: `./gradlew spotlessCheck`

---

**Task Completed**: January 18, 2026  
**Completed By**: Marco Ferretti
