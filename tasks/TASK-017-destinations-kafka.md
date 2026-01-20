# TASK-017: Destinations Module - Kafka Adapter

**Status**: ⏸️ Not Started  
**Priority**: P1 (High)  
**Phase**: 4 - Destinations  
**Dependencies**: TASK-013 (JSON Serializer), TASK-016 (File Destination)  
**Human Supervision**: LOW (standard Kafka producer)

---

## Objective

Implement Kafka destination adapter that publishes generated records to Kafka topics with configurable batching, compression, and authentication (SASL/SSL).

---

## Background

Kafka is a primary use case for test data generation:
- Load testing Kafka consumers
- Populating test topics
- Streaming data pipelines

**Requirements**:
- Producer connection pooling
- Batching for throughput
- Compression (gzip, snappy, lz4)
- Authentication (SASL_PLAIN, SASL_SCRAM, SSL)
- Partition key support
- Error handling and retries

---

## Implementation Details

### Step 1: Add Kafka Dependency

**File**: `destinations/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    
    // Kafka (compileOnly - users provide at runtime)
    compileOnly("org.apache.kafka:kafka-clients:3.6.1")
    
    // For testing
    testImplementation("org.apache.kafka:kafka-clients:3.6.1")
}
```

---

### Step 2: Create KafkaDestination

**File**: `destinations/src/main/java/com/datagenerator/destinations/KafkaDestination.java`

```java
package com.datagenerator.destinations;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Kafka destination adapter for publishing records to Kafka topics.
 */
@Slf4j
public class KafkaDestination implements DestinationAdapter {
    
    private final KafkaProducer<String, byte[]> producer;
    private final String topic;
    private final boolean sync;
    private long recordCount = 0;
    
    /**
     * Create Kafka destination with configuration.
     * 
     * @param config Kafka configuration map
     */
    public KafkaDestination(java.util.Map<String, Object> config) {
        this.topic = (String) config.get("topic");
        if (topic == null || topic.isBlank()) {
            throw new DestinationException("Kafka topic is required");
        }
        
        this.sync = Boolean.TRUE.equals(config.get("sync"));
        
        Properties props = buildProducerProperties(config);
        this.producer = new KafkaProducer<>(props);
        
        log.info("Created Kafka destination: topic={}, sync={}", topic, sync);
    }
    
    private Properties buildProducerProperties(java.util.Map<String, Object> config) {
        Properties props = new Properties();
        
        // Required properties
        String bootstrap = (String) config.get("bootstrap");
        if (bootstrap == null) {
            throw new DestinationException("Kafka bootstrap servers required");
        }
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        
        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        
        // Performance tuning
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 
            config.getOrDefault("batch_size", 16384));
        props.put(ProducerConfig.LINGER_MS_CONFIG, 
            config.getOrDefault("linger_ms", 10));
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, 
            config.getOrDefault("compression", "none"));
        props.put(ProducerConfig.ACKS_CONFIG, 
            config.getOrDefault("acks", "1"));
        
        // Authentication (SASL/SSL)
        if (config.containsKey("security_protocol")) {
            props.put("security.protocol", config.get("security_protocol"));
        }
        if (config.containsKey("sasl_mechanism")) {
            props.put("sasl.mechanism", config.get("sasl_mechanism"));
        }
        if (config.containsKey("sasl_jaas_config")) {
            props.put("sasl.jaas.config", config.get("sasl_jaas_config"));
        }
        
        // SSL properties
        if (config.containsKey("ssl_truststore_location")) {
            props.put("ssl.truststore.location", config.get("ssl_truststore_location"));
            props.put("ssl.truststore.password", config.get("ssl_truststore_password"));
        }
        
        return props;
    }
    
    @Override
    public void write(byte[] record) {
        try {
            ProducerRecord<String, byte[]> producerRecord = 
                new ProducerRecord<>(topic, null, record);
            
            if (sync) {
                // Synchronous send (wait for ack)
                producer.send(producerRecord).get();
            } else {
                // Asynchronous send
                producer.send(producerRecord, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Failed to send record to Kafka", exception);
                    } else {
                        log.trace("Sent record to partition {} offset {}", 
                            metadata.partition(), metadata.offset());
                    }
                });
            }
            
            recordCount++;
            
            if (recordCount % 10000 == 0) {
                log.info("Sent {} records to Kafka", recordCount);
            }
            
        } catch (InterruptedException | ExecutionException e) {
            throw new DestinationException("Failed to send record to Kafka", e);
        }
    }
    
    @Override
    public void flush() {
        log.debug("Flushing Kafka producer");
        producer.flush();
    }
    
    @Override
    public void close() throws Exception {
        flush();
        producer.close();
        log.info("Closed Kafka destination - total records sent: {}", recordCount);
    }
}
```

---

### Step 3: Create Kafka Configuration Model

**File**: `schema/src/main/java/com/datagenerator/schema/config/KafkaDestinationConfig.java`

```java
package com.datagenerator.schema.config;

import lombok.Value;
import java.util.Map;

/**
 * Configuration model for Kafka destination.
 */
@Value
public class KafkaDestinationConfig {
    String bootstrap;
    String topic;
    Integer batchSize;
    Integer lingerMs;
    String compression;
    String acks;
    Boolean sync;
    
    // Authentication
    String securityProtocol;
    String saslMechanism;
    String saslJaasConfig;
    
    // SSL
    String sslTruststoreLocation;
    String sslTruststorePassword;
    
    public static KafkaDestinationConfig fromMap(Map<String, Object> config) {
        return new KafkaDestinationConfig(
            (String) config.get("bootstrap"),
            (String) config.get("topic"),
            (Integer) config.get("batch_size"),
            (Integer) config.get("linger_ms"),
            (String) config.get("compression"),
            (String) config.get("acks"),
            (Boolean) config.get("sync"),
            (String) config.get("security_protocol"),
            (String) config.get("sasl_mechanism"),
            (String) config.get("sasl_jaas_config"),
            (String) config.get("ssl_truststore_location"),
            (String) config.get("ssl_truststore_password")
        );
    }
}
```

---

### Step 4: Write Unit Tests

**File**: `destinations/src/test/java/com/datagenerator/destinations/KafkaDestinationTest.java`

```java
package com.datagenerator.destinations;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class KafkaDestinationTest {
    
    @Test
    void shouldThrowExceptionWhenTopicMissing() {
        Map<String, Object> config = Map.of(
            "bootstrap", "localhost:9092"
        );
        
        assertThatThrownBy(() -> new KafkaDestination(config))
            .isInstanceOf(DestinationException.class)
            .hasMessageContaining("topic");
    }
    
    @Test
    void shouldThrowExceptionWhenBootstrapMissing() {
        Map<String, Object> config = Map.of(
            "topic", "test-topic"
        );
        
        assertThatThrownBy(() -> new KafkaDestination(config))
            .isInstanceOf(DestinationException.class)
            .hasMessageContaining("bootstrap");
    }
    
    // Integration tests with Testcontainers in TASK-023
}
```

---

## Acceptance Criteria

- ✅ Connects to Kafka brokers
- ✅ Publishes records to specified topic
- ✅ Supports async and sync modes
- ✅ Configurable batching and compression
- ✅ SASL/SSL authentication support
- ✅ Proper error handling and logging
- ✅ Clean resource cleanup on close
- ✅ Unit tests pass

---

## Configuration Example

**File**: `config/jobs/kafka_address.yaml`

```yaml
source: address.yaml
seed:
  type: embedded
  value: 12345
destination: kafka
conf:
  bootstrap: localhost:9092
  topic: test-addresses
  batch_size: 1000
  linger_ms: 10
  compression: gzip
  acks: "1"
  sync: false
```

**With SASL Authentication**:
```yaml
conf:
  bootstrap: kafka.example.com:9093
  topic: addresses
  security_protocol: SASL_SSL
  sasl_mechanism: SCRAM-SHA-512
  sasl_jaas_config: |
    org.apache.kafka.common.security.scram.ScramLoginModule required
    username="user"
    password="secret";
  ssl_truststore_location: /path/to/truststore.jks
  ssl_truststore_password: truststore-password
```

---

## Testing

Unit tests:
```bash
./gradlew :destinations:test
```

Integration tests with Testcontainers (TASK-023):
```bash
./gradlew :destinations:integrationTest
```

---

**Completion Date**: [Mark when complete]
