# US-022: Integration Test Infrastructure

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: None  
**Completion Date**: March 6, 2026

---

## User Story

As a **developer**, I want **Testcontainers infrastructure for integration tests** so that **I can test Kafka, PostgreSQL, and MySQL destinations with real containerized services**.

---

## Acceptance Criteria

- ✅ Testcontainers dependencies added to all test scopes
- ✅ KafkaContainer configured and reusable
- ✅ PostgreSQLContainer configured and reusable
- ✅ MySQLContainer configured and reusable
- ✅ Base IntegrationTest class with shared containers
- ✅ Separate `integrationTest` Gradle task
- ✅ Tests tagged with `@Tag("integration")`
- ✅ Containers start cleanly and shut down properly
- ✅ Fast startup (use Alpine images where possible)
- ✅ Documentation for running integration tests

---

## Implementation Notes

### Testcontainers Dependencies
Add to all module test dependencies:
```kotlin
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
testImplementation("org.testcontainers:kafka:1.19.3")
testImplementation("org.testcontainers:postgresql:1.19.3")
testImplementation("org.testcontainers:mysql:1.19.3")
```

### Base Integration Test Class
```java
@Testcontainers
@Tag("integration")
public abstract class IntegrationTest {
    
    @Container
    protected static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
    
    @Container
    protected static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:16-alpine");
    
    @Container
    protected static MySQLContainer<?> mysql = 
        new MySQLContainer<>("mysql:8.0");
}
```

### Gradle Task
```kotlin
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests"
    group = "verification"
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
```

---

## Testing Requirements

### Smoke Tests
- Containers start successfully
- Kafka broker is reachable
- PostgreSQL accepts connections
- MySQL accepts connections
- Containers shut down cleanly

### Performance
- Container startup time < 30 seconds
- Tests run in reasonable time
- Containers reuse across test methods

---

## Definition of Done

- [ ] Testcontainers dependencies added
- [ ] Base IntegrationTest class created
- [ ] Kafka, PostgreSQL, MySQL containers configured
- [ ] `integrationTest` Gradle task created
- [ ] Tests tagged with `@Tag("integration")`
- [ ] Smoke tests verify containers work
- [ ] Documentation in README for running tests
- [ ] CI/CD updated to run integration tests
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
