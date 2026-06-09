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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.json.JsonSerializer;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;

class KafkaDestinationTest {
  private static final String BOOTSTRAP = "localhost:9092";
  private static final String TOPIC = "test-topic";

  @Test
  void shouldThrowExceptionWhenBootstrapMissing() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(null).topic(TOPIC).build();

    var serializer = new JsonSerializer();
    assertThatThrownBy(() -> new KafkaDestination(config, serializer))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("bootstrap");
  }

  @Test
  void shouldThrowExceptionWhenBootstrapBlank() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap("   ").topic(TOPIC).build();

    var serializer = new JsonSerializer();
    assertThatThrownBy(() -> new KafkaDestination(config, serializer))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("bootstrap");
  }

  @Test
  void shouldThrowExceptionWhenTopicMissing() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(BOOTSTRAP).topic(null).build();

    var serializer = new JsonSerializer();
    assertThatThrownBy(() -> new KafkaDestination(config, serializer))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void shouldThrowExceptionWhenTopicBlank() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(BOOTSTRAP).topic("").build();

    var serializer = new JsonSerializer();
    assertThatThrownBy(() -> new KafkaDestination(config, serializer))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void shouldThrowExceptionWhenWritingBeforeOpen() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(BOOTSTRAP).topic(TOPIC).build();

    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      Map<String, Object> data = Map.of("name", "John", "age", 42);

      assertThatThrownBy(() -> destination.write(data))
          .isInstanceOf(DestinationException.class)
          .hasMessageContaining("not open");
    }
  }

  @Test
  void shouldReturnCorrectDestinationType() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder().bootstrap(BOOTSTRAP).topic(TOPIC).build();

    try (KafkaDestination destination = new KafkaDestination(config, new JsonSerializer())) {
      assertThat(destination.getDestinationType()).isEqualTo("kafka");
    }
  }

  @Test
  void shouldAllowValidConfiguration() {
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(BOOTSTRAP)
            .topic(TOPIC)
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
            .bootstrap(BOOTSTRAP)
            .topic(TOPIC)
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
            .bootstrap(BOOTSTRAP)
            .topic(TOPIC)
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

  @Test
  @SuppressWarnings("unchecked")
  void shouldRetrySyncSendOnTransientFailure() throws Exception {
    Future<RecordMetadata> failedFuture = mock(Future.class);
    when(failedFuture.get()).thenThrow(new ExecutionException(new RuntimeException("transient")));

    Future<RecordMetadata> successFuture = mock(Future.class);
    when(successFuture.get()).thenReturn(null);

    Producer<String, byte[]> mockProducer = mock(Producer.class);
    when(mockProducer.send(any())).thenReturn(failedFuture, successFuture);

    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(BOOTSTRAP)
            .topic(TOPIC)
            .sync(true)
            .maxRetries(3)
            .retryDelayMs(0)
            .build();

    KafkaDestination dest = new KafkaDestination(config, new JsonSerializer(), mockProducer);
    dest.write(Map.of("key", "value")); // should succeed after retry

    verify(mockProducer, times(2)).send(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldFailAfterExhaustingRetriesForSyncSend() throws Exception {
    Future<RecordMetadata> failedFuture = mock(Future.class);
    when(failedFuture.get())
        .thenThrow(new ExecutionException(new RuntimeException("persistent failure")));

    Producer<String, byte[]> mockProducer = mock(Producer.class);
    when(mockProducer.send(any())).thenReturn(failedFuture);

    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap(BOOTSTRAP)
            .topic(TOPIC)
            .sync(true)
            .maxRetries(2)
            .retryDelayMs(0)
            .build();

    KafkaDestination dest = new KafkaDestination(config, new JsonSerializer(), mockProducer);

    Map<String, Object> msg = Map.of("key", "value");
    assertThatThrownBy(() -> dest.write(msg))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("failed after 2 attempt");

    verify(mockProducer, times(2)).send(any());
  }

  // Note: Integration tests with actual Kafka broker using Testcontainers
  // will be implemented in TASK-023.
}
