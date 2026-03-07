# US-023: Kafka Integration Tests

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: US-017, US-022  
**Completion Date**: March 6, 2026

---

## User Story

As a **developer**, I want **integration tests for Kafka destination** so that **I can verify records are published correctly to Kafka topics with proper batching and compression**.

---

## Acceptance Criteria

- ✅ Publish records to Kafka using Testcontainers
- ✅ Consume and verify message content matches generated records
- ✅ Test batching behavior (batch_size parameter)
- ✅ Test all compression modes (none, gzip, snappy, lz4, zstd)
- ✅ Test synchronous and asynchronous send modes
- ✅ Test error scenarios (invalid topic, connection loss)
- ✅ Verify message order is preserved
- ✅ Verify no message loss

---

## Implementation Notes

### Test Scenarios
1. **Basic publishing**: 100 records to topic, verify all received
2. **Batching**: Different batch sizes, verify performance impact
3. **Compression**: Each compression type, verify messages readable
4. **Sync vs Async**: Compare behavior and performance
5. **Error handling**: Invalid configuration, verify clear errors

### Kafka Consumer Verification
Create test consumer to read and verify published messages:
```java
try (KafkaConsumer<String, byte[]> consumer = createConsumer()) {
    consumer.subscribe(List.of("test-topic"));
    var records = consumer.poll(Duration.ofSeconds(10));
    assertThat(records.count()).isEqualTo(100);
    // Verify content
}
```

### Test Structure
```java
@Test
@Tag("integration")
void shouldPublishRecordsToKafka() {
    // Setup: Create KafkaDestination with Testcontainers bootstrap
    // Execute: Publish 100 records
    // Verify: Consume and check all records
}
```

---

## Testing Requirements

### Functional Tests
- 100 records published and consumed successfully
- Message content matches generated records (JSON parsing)
- Batch size affects throughput as expected
- Each compression type works

### Performance Tests
- Measure throughput with batching
- Compare compression impact on throughput
- Verify async faster than sync

### Error Tests
- Missing bootstrap servers
- Invalid topic name
- Connection timeout
- Authentication failure (if applicable)

---

## Definition of Done

- [ ] Test class extends IntegrationTest
- [ ] Tests for publishing and consuming
- [ ] Tests for batching behavior
- [ ] Tests for all compression modes
- [ ] Tests for sync/async modes
- [ ] Error scenario tests
- [ ] Test coverage >= 85%
- [ ] Tests pass consistently
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
