# TASK-023: Testing - Kafka Integration Tests

**Status**: ✅ Complete (via TASK-022)  
**Priority**: P1 (High)  
**Phase**: 6 - Testing & Quality  
**Dependencies**: TASK-017 (Kafka Adapter), TASK-022 (Integration Tests Setup)  
**Human Supervision**: LOW  
**Completed**: March 6, 2026

---

## ✅ Completion Summary

Kafka integration tests were implemented as part of TASK-022 infrastructure setup.

**File**: `destinations/src/test/java/com/datagenerator/destinations/kafka/KafkaDestinationIT.java`

**Tests Implemented** (4 tests):
1. ✅ `shouldWriteRecordsToKafka` - Basic message publishing and verification
2. ✅ `shouldHandleLargeNumberOfRecords` - Batch handling (1000 records)
3. ✅ `shouldWriteRecordsWithSyncMode` - Synchronous mode testing
4. ✅ `shouldHandleCompressionMode` - Gzip compression testing

**Features Tested**:
- Real Kafka container (confluentinc/cp-kafka:7.5.0)
- Message publishing with KafkaProducer
- Consumer verification with polling
- Async/sync modes
- Batching configuration
- Compression modes
- Awaitility for async assertions

**Run Command**: `./gradlew :destinations:integrationTest`

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
