# TASK-044: Extras Directory — External JARs and Custom Datafaker Providers

**Status:** ⏸️ Not Started
**Priority:** P2 (Medium)
**Phase:** Phase 9 — Distribution & Runtime Extensibility
**Estimated Effort:** 3–5 hours
**Complexity:** Low–Medium
**Dependencies:** TASK-018 ✅ (database destination exists), TASK-010 ✅ (Datafaker integration exists)
**Human Supervision:** LOW

---

## Goal

Introduce an `extras/` directory in the SeedStream distribution that users populate at runtime with:

1. **JDBC drivers** (PostgreSQL, MySQL, or any other JDBC-compliant driver) — currently bundled as `runtimeOnly` Gradle dependencies, which creates a licensing entanglement (MySQL Connector/J is GPL 2.0 + FOSS Exception) and couples the distribution to specific driver versions.
2. **Custom Datafaker provider JARs** — third-party or user-authored Datafaker providers that extend the set of available fake data types, loaded transparently via the classpath.

Both are solved by the same mechanism: all JARs placed in `extras/` are added to the application classpath at startup via the Gradle-generated launch scripts.

---

## Background

### Why JDBC drivers must not be bundled

- **Licensing**: MySQL Connector/J is GPL 2.0. While Oracle's FOSS Exception permits use with Apache 2.0 projects, the exception is conditional on SeedStream remaining FOSS-licensed. Bundling a GPL artifact couples our distribution to that constraint in perpetuity.
- **Version coupling**: Bundling specific driver versions forces users onto those versions. Enterprises frequently need to match their database server version exactly.
- **Unnecessary**: The `destinations` module only uses `java.sql.*` (JDK interfaces). Driver JARs have **zero compile-time API surface** — they register themselves via `java.sql.DriverManager` at runtime. The current `compileOnly` entries in `destinations/build.gradle.kts` are entirely redundant.

### Standard pattern

This is the established pattern for database tools (Flyway, Liquibase, Gradle itself, Logstash all do this):
- The tool ships without drivers
- Users drop the appropriate driver JAR into a well-known directory (`drivers/`, `extras/`, `lib/ext/`, etc.)
- The startup script includes that directory in `CLASSPATH`

### Custom Datafaker providers

Datafaker supports custom providers via the Java Service Loader mechanism (`META-INF/services/net.datafaker.providers.base.AbstractProvider`). If a custom provider JAR is on the classpath at startup, Datafaker discovers it automatically. No SeedStream code changes are required beyond ensuring `extras/` is on the classpath.

---

## Changes Required

### 1. `destinations/build.gradle.kts`

Remove the redundant `compileOnly` driver entries. Keep `testImplementation` for integration tests.

**Before:**
```kotlin
compileOnly(libs.postgresql)
compileOnly(libs.mysql.connector.j)
testImplementation(libs.h2)
testImplementation(libs.postgresql)
```

**After:**
```kotlin
testImplementation(libs.h2)
testImplementation(libs.postgresql)
```

### 2. `cli/build.gradle.kts`

Remove `runtimeOnly` driver entries. Add `extras/` to the distribution contents and customize `startScripts` to prepend `extras/*` to the classpath.

**Before:**
```kotlin
runtimeOnly(libs.kafka.clients)
runtimeOnly(libs.postgresql)
runtimeOnly(libs.mysql.connector.j)
```

**After:**
```kotlin
runtimeOnly(libs.kafka.clients)
```

Add distribution and script customization:

```kotlin
distributions {
    main {
        contents {
            from("src/dist/extras") {
                into("extras")
            }
        }
    }
}

tasks.startScripts {
    doLast {
        // Unix: prepend extras/* to CLASSPATH
        unixScript.text = unixScript.text.replace(
            "CLASSPATH=\$APP_HOME/lib/",
            "CLASSPATH=\$APP_HOME/extras/*:\$APP_HOME/lib/"
        )
        // Windows: prepend extras\* to CLASSPATH
        windowsScript.text = windowsScript.text.replace(
            "set CLASSPATH=%APP_HOME%\\lib\\",
            "set CLASSPATH=%APP_HOME%\\extras\\*;%APP_HOME%\\lib\\"
        )
    }
}
```

> **Note**: The exact replacement string must be verified against the actual generated script output after applying. Run `./gradlew :cli:installDist` and inspect `cli/build/install/cli/bin/cli` to confirm the pattern.

### 3. `cli/src/dist/extras/README.txt`

Create this file so the `extras/` directory is not empty in the distribution and users have immediate guidance.

```text
SeedStream — extras/ directory
===============================

Place any additional JAR files here. They are automatically added to the
classpath when SeedStream starts.

JDBC DRIVERS
------------
SeedStream does not bundle JDBC drivers. To use the database destination,
download the appropriate driver JAR and place it here:

  PostgreSQL:  https://jdbc.postgresql.org/download/
               e.g. postgresql-42.7.3.jar

  MySQL:       https://dev.mysql.com/downloads/connector/j/
               Select "Platform Independent", extract the .jar
               e.g. mysql-connector-j-8.3.0.jar

  Other:       Any JDBC 4.x-compliant driver should work.

CUSTOM DATAFAKER PROVIDERS
---------------------------
SeedStream uses Datafaker for realistic data generation. You can extend
the available data types by adding a custom Datafaker provider JAR here.

A custom provider must:
  1. Implement a class extending AbstractProvider<BaseProviders>
  2. Register it via META-INF/services/net.datafaker.providers.base.AbstractProvider
  3. Be packaged as a JAR

See: https://www.datafaker.net/documentation/custom-providers/

EXAMPLE
-------
  extras/
  ├── postgresql-42.7.3.jar       ← JDBC driver for PostgreSQL
  └── my-custom-faker-1.0.0.jar   ← custom Datafaker provider
```

### 4. `NOTICE` file

Remove entries for PostgreSQL JDBC Driver and MySQL Connector/J — they are no longer distributed with SeedStream. Users obtain them independently under their own terms.

### 5. `gradle/libs.versions.toml`

No changes needed. The `postgresql` and `mysql.connector.j` entries remain — they are still used in `testImplementation` scope for integration tests. They are simply no longer included in production distribution artifacts.

---

## Testing

### Unit/Integration tests — no impact

`DatabaseDestinationTest` uses H2 (in-memory, remains `testImplementation`).
`DatabaseDestinationIT` uses Testcontainers which injects the PostgreSQL driver into the test JVM independently of the distribution — `testImplementation(libs.postgresql)` in `destinations/build.gradle.kts` covers this.

### Manual verification

After `./gradlew :cli:installDist`:

1. Confirm `cli/build/install/cli/extras/README.txt` exists.
2. Confirm `cli/build/install/cli/lib/` does NOT contain `postgresql-*.jar` or `mysql-connector-j-*.jar`.
3. Inspect `cli/build/install/cli/bin/cli` — the `CLASSPATH` line should start with `$APP_HOME/extras/*:`.
4. Run without a driver — confirm the error is a clear `No suitable driver found` (not a classpath error).
5. Drop a PostgreSQL driver JAR into `extras/` and run a database job — confirm it connects successfully.

---

## Acceptance Criteria

- [ ] `postgresql` and `mysql.connector.j` are NOT present in the distribution `lib/` directory
- [ ] `extras/README.txt` is present in the distribution
- [ ] `extras/*` appears before `lib/*` in the classpath of the generated startup scripts (Unix + Windows)
- [ ] All existing unit and integration tests pass (`./gradlew test`)
- [ ] E2E database benchmark continues to pass when a PostgreSQL driver JAR is placed in `extras/`
- [ ] `NOTICE` file no longer lists PostgreSQL JDBC Driver or MySQL Connector/J
- [ ] `compileOnly(libs.postgresql)` and `compileOnly(libs.mysql.connector.j)` removed from `destinations/build.gradle.kts`

---

## Files Modified

| File | Change |
|---|---|
| `destinations/build.gradle.kts` | Remove `compileOnly` driver entries |
| `cli/build.gradle.kts` | Remove `runtimeOnly` driver entries; add `distributions` block and `startScripts` customization |
| `cli/src/dist/extras/README.txt` | Create (new file) |
| `NOTICE` | Remove PostgreSQL JDBC Driver and MySQL Connector/J entries |

---

**Estimated Effort:** 3–5 hours
**Risk:** Low — test scope is unaffected; the only runtime impact is that a driver JAR must be present in `extras/` for database jobs to work.
