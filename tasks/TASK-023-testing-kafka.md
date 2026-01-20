# TASK-023: Testing - Kafka Integration Tests

**Status**: 🔒 Blocked  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: TASK-017 (Kafka Adapter), TASK-022 (Integration Tests Setup)  
**Human Supervision**: LOW

---

## Objective

Write integration tests for Kafka destination using Testcontainers, verifying records are published correctly.

---

## Implementation Details

### Test Scenarios
1. Publish to Kafka topic
2. Verify message content and format
3. Test batching behavior
4. Test compression modes
5. Test error handling (invalid topic, connection loss)

### Example Test

```java
@Test
@Tag("integration")
void shouldPublishRecordsToKafka() {
    // Create Kafka destination
    Map<String, Object> config = Map.of(
        "bootstrap", kafka.getBootstrapServers(),
        "topic", "test-topic",
        "batch_size", 10
    );
    
    KafkaDestination destination = new KafkaDestination(config);
    
    // Write records
    for (int i = 0; i < 100; i++) {
        String json = String.format("{\"id\":%d,\"name\":\"Test%d\"}", i, i);
        destination.write(json.getBytes());
    }
    
    destination.close();
    
    // Consume and verify
    try (KafkaConsumer<String, String> consumer = createConsumer()) {
        consumer.subscribe(List.of("test-topic"));
        
        var records = consumer.poll(Duration.ofSeconds(10));
        assertThat(records.count()).isEqualTo(100);
    }
}
```

---

## Acceptance Criteria

- ✅ Records published to Kafka
- ✅ Message content verified
- ✅ Batching works correctly
- ✅ All compression modes tested
- ✅ Error scenarios handled

---

**Completion Date**: [Mark when complete]
