/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.destinations.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.json.JsonSerializer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KafkaDestinationTest {

  @Test
  void shouldThrowExceptionWhenBootstrapMissing() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(null).topic("test-topic").build();

    assertThatThrownBy(() -> new KafkaDestination(config, new JsonSerializer()))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("bootstrap");
  }

  @Test
  void shouldThrowExceptionWhenBootstrapBlank() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("   ").topic("test-topic").build();

    assertThatThrownBy(() -> new KafkaDestination(config, new JsonSerializer()))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("bootstrap");
  }

  @Test
  void shouldThrowExceptionWhenTopicMissing() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("localhost:9092").topic(null).build();

    assertThatThrownBy(() -> new KafkaDestination(config, new JsonSerializer()))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void shouldThrowExceptionWhenTopicBlank() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("localhost:9092").topic("").build();

    assertThatThrownBy(() -> new KafkaDestination(config, new JsonSerializer()))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void shouldThrowExceptionWhenWritingBeforeOpen() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("localhost:9092").topic("test-topic").build();

    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      Map<String, Object> record = Map.of("name", "John", "age", 42);

      assertThatThrownBy(() -> destination.write(record))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("not open");
    }
  }

  @Test
  void shouldReturnCorrectDestinationType() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("localhost:9092").topic("test-topic").build();

    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      assertThat(destination.getDestinationType()).isEqualTo("kafka");
    }
  }

  @Test
  void shouldAllowValidConfiguration() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap("localhost:9092")
            .topic("test-topic")
            .sync(true)
            .batchSize(32768)
            .lingerMs(50)
            .compression("gzip")
            .acks("all")
            .build();

    // Should not throw exception
    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      assertThat(destination).isNotNull();
      assertThat(destination.getDestinationType()).isEqualTo("kafka");
    }
  }

  @Test
  void shouldAllowConfigurationWithSaslAuth() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap("localhost:9092")
            .topic("test-topic")
            .securityProtocol("SASL_SSL")
            .saslMechanism("SCRAM-SHA-512")
            .saslJaasConfig(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"user\" password=\"pass\";")
            .build();

    // Should not throw exception
    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      assertThat(destination).isNotNull();
    }
  }

  @Test
  void shouldAllowConfigurationWithSslAuth() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap("localhost:9092")
            .topic("test-topic")
            .securityProtocol("SSL")
            .sslTruststoreLocation("/path/to/truststore.jks")
            .sslTruststorePassword("truststore-password")
            .sslKeystoreLocation("/path/to/keystore.jks")
            .sslKeystorePassword("keystore-password")
            .build();

    // Should not throw exception
    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      assertThat(destination).isNotNull();
    }
  }

  // Note: Integration tests with actual Kafka broker using Testcontainers
  // will be implemented in TASK-023.
}
