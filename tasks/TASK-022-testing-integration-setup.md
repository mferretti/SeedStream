# TASK-022: Testing - Integration Tests Setup

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: None  
**Human Supervision**: LOW

---

## Objective

Set up infrastructure for integration tests using Testcontainers for Kafka, PostgreSQL, and MySQL.

---

## Implementation Details

### Add Dependencies

```kotlin
// All module test dependencies
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
testImplementation("org.testcontainers:kafka:1.19.3")
testImplementation("org.testcontainers:postgresql:1.19.3")
testImplementation("org.testcontainers:mysql:1.19.3")
```

### Base Test Class

```java
@Testcontainers
public abstract class IntegrationTest {
    
    @Container
    protected static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );
    
    @Container
    protected static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:16-alpine"
    );
    
    @BeforeAll
    static void setup() {
        // Wait for containers to be ready
    }
}
```

### Gradle Integration Test Task

```kotlin
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    
    useJUnitPlatform {
        includeTags("integration")
    }
    
    shouldRunAfter(tasks.test)
}
```

---

## Acceptance Criteria

- ✅ Testcontainers configured
- ✅ Kafka, PostgreSQL, MySQL containers available
- ✅ Separate `integrationTest` task
- ✅ Tagged with `@Tag("integration")`
- ✅ Containers start and stop cleanly

---

**Completion Date**: [Mark when complete]
