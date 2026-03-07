# US-017: Kafka Output Destination

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 4 - Destinations  
**Dependencies**: US-013, US-016  
**Completion Date**: March 2026

---

## User Story

As a **Kafka engineer**, I want **to publish generated records directly to Kafka topics** so that **I can load test consumers, populate test topics, and simulate streaming data pipelines**.

---

## Acceptance Criteria

- ✅ KafkaDestination publishes records to Kafka topics
- ✅ Configurable Kafka producer properties (bootstrap servers, topic, batch size, etc.)
- ✅ Support for compression (none, gzip, snappy, lz4, zstd)
- ✅ Support for authentication (SASL_PLAIN, SASL_SCRAM, SSL)
- ✅ Synchronous and asynchronous send modes
- ✅ Producer connection pooling (reuse producer instance)
- ✅ Batching for high throughput
- ✅ Error handling with retries
- ✅ Progress logging (every 10,000 records)
- ✅ Proper cleanup on close (flush before close)

---

## Implementation Notes

### KafkaDestination Configuration
From job YAML `conf` section:
- **bootstrap**: Kafka broker addresses
- **topic**: Target topic name
- **batch_size**: Records per batch (default: 16384)
- **linger_ms**: Batch delay in ms (default: 10)
- **compression**: Compression type (default: none)
- **acks**: Acknowledgment mode (default: 1)
- **sync**: Synchronous send (default: false)

### Authentication Configuration
**SASL/PLAIN**:
```yaml
security_protocol: SASL_SSL
sasl_mechanism: PLAIN
sasl_jaas_config: "org.apache.kafka.common.security.plain.PlainLoginModule required username='...' password='...';"
```

**SSL**:
```yaml
security_protocol: SSL
ssl_truststore_location: /path/to/truststore.jks
ssl_truststore_password: password
```

### Producer Configuration
- ByteArraySerializer for value (already serialized)
- StringSerializer for key (null or generated)
- Batching and compression configured
- Proper error callbacks for async sends

---

## Testing Requirements

### Unit Tests (Mocked)
- Build producer properties correctly
- Validate required configuration
- Handle missing bootstrap servers
- Handle missing topic

### Integration Tests (Testcontainers)
- Publish 100 records to Kafka topic
- Consume and verify record content
- Test batching behavior
- Test compression modes (gzip, snappy, lz4)
- Test authentication (if possible with Testcontainers)
- Test error handling (invalid topic)

### Performance Tests
- Measure throughput (records/second)
- Test with different batch sizes
- Test with different compression types
- Verify no message loss

---

## Definition of Done

- [ ] KafkaDestination implements DestinationAdapter
- [ ] Producer configuration from job conf map
- [ ] Support for compression types
- [ ] Support for authentication (SASL, SSL)
- [ ] Synchronous and asynchronous modes
- [ ] Error handling and retries
- [ ] Progress logging
- [ ] Unit tests with mocked producer
- [ ] Integration tests with Testcontainers Kafka
- [ ] Performance tests showing high throughput
- [ ] Test coverage >= 85%
- [ ] Documentation with configuration examples
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
