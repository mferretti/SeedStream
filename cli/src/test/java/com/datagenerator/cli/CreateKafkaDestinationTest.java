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

package com.datagenerator.cli;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.destinations.kafka.KafkaDestination;
import com.datagenerator.destinations.kafka.KafkaDestinationConfig;
import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.schema.secret.SecretResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Kafka destination wiring in {@link ExecuteCommand} — the optional-settings and
 * security-settings helpers and the end-to-end builder. No broker is needed: the {@link
 * KafkaDestination} constructor only validates and stores its config.
 */
class CreateKafkaDestinationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Plain values pass through {@link com.datagenerator.schema.secret.ConfigSubstitutor}. */
  private static final SecretResolver PASSTHROUGH = path -> "resolved-" + path;

  /** Minimal serializer double — the Kafka destination only stores it, never invokes it here. */
  private static final FormatSerializer STUB_SERIALIZER =
      new FormatSerializer() {
        @Override
        public String serialize(Map<String, Object> data) {
          return data.toString();
        }

        @Override
        public String getFormatName() {
          return "json";
        }
      };

  private static JsonNode json(String s) {
    try {
      return MAPPER.readTree(s);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void applyOptionalKafkaSettingsShouldMapEveryOptionalField() {
    KafkaDestinationConfig.KafkaDestinationConfigBuilder builder =
        KafkaDestinationConfig.builder().bootstrap("b:9092").topic("t");

    ExecuteCommand.applyOptionalKafkaSettings(
        builder,
        json(
            "{\"sync\":true,\"batch_size\":4096,\"linger_ms\":42,\"compression\":\"gzip\","
                + "\"acks\":\"1\",\"max_retries\":7,\"retry_delay_ms\":250}"));

    KafkaDestinationConfig cfg = builder.build();
    assertThat(cfg.isSync()).isTrue();
    assertThat(cfg.getBatchSize()).isEqualTo(4096);
    assertThat(cfg.getLingerMs()).isEqualTo(42);
    assertThat(cfg.getCompression()).isEqualTo("gzip");
    assertThat(cfg.getAcks()).isEqualTo("1");
    assertThat(cfg.getMaxRetries()).isEqualTo(7);
    assertThat(cfg.getRetryDelayMs()).isEqualTo(250L);
  }

  @Test
  void applyKafkaSecuritySettingsShouldMapProtocolSaslAndSsl() {
    KafkaDestinationConfig.KafkaDestinationConfigBuilder builder =
        KafkaDestinationConfig.builder().bootstrap("b:9092").topic("t");

    ExecuteCommand.applyKafkaSecuritySettings(
        builder,
        json(
            "{\"ssl_truststore_location\":\"/ts.jks\",\"ssl_truststore_password\":\"tspw\","
                + "\"ssl_keystore_location\":\"/ks.jks\",\"ssl_keystore_password\":\"kspw\"}"),
        "SASL_SSL",
        "PLAIN",
        "jaas-config;",
        PASSTHROUGH);

    KafkaDestinationConfig cfg = builder.build();
    assertThat(cfg.getSecurityProtocol()).isEqualTo("SASL_SSL");
    assertThat(cfg.getSaslMechanism()).isEqualTo("PLAIN");
    assertThat(cfg.getSaslJaasConfig()).isEqualTo("jaas-config;");
    assertThat(cfg.getSslTruststoreLocation()).isEqualTo("/ts.jks");
    assertThat(cfg.getSslTruststorePassword()).isEqualTo("tspw");
    assertThat(cfg.getSslKeystoreLocation()).isEqualTo("/ks.jks");
    assertThat(cfg.getSslKeystorePassword()).isEqualTo("kspw");
  }

  @Test
  void createKafkaDestinationShouldBuildFromFullConfig() {
    JsonNode conf =
        json(
            "{\"bootstrap\":\"broker:9092\",\"topic\":\"events\",\"sync\":true,"
                + "\"batch_size\":8192,\"linger_ms\":5,\"compression\":\"snappy\",\"acks\":\"all\","
                + "\"max_retries\":2,\"retry_delay_ms\":100,\"security_protocol\":\"SASL_SSL\","
                + "\"sasl_mechanism\":\"PLAIN\",\"username\":\"alice\",\"password\":\"s3cret\","
                + "\"ssl_truststore_location\":\"/ts.jks\"}");

    KafkaDestination dest =
        new ExecuteCommand().createKafkaDestination(conf, STUB_SERIALIZER, PASSTHROUGH);

    assertThat(dest).isNotNull();
    assertThat(dest.getDestinationType()).isEqualTo("kafka");
  }

  @Test
  void createKafkaDestinationShouldWarnWhenBothJaasAndCredentialsGiven() {
    // Both an explicit jaas config and username/password set — explicit jaas wins; exercises the
    // precedence-warning branch.
    JsonNode conf =
        json(
            "{\"bootstrap\":\"broker:9092\",\"topic\":\"events\",\"security_protocol\":\"SASL_SSL\","
                + "\"sasl_mechanism\":\"PLAIN\","
                + "\"sasl_jaas_config\":\"org.example.Login required;\","
                + "\"username\":\"alice\",\"password\":\"s3cret\"}");

    KafkaDestination dest =
        new ExecuteCommand().createKafkaDestination(conf, STUB_SERIALIZER, PASSTHROUGH);

    assertThat(dest).isNotNull();
    assertThat(dest.getDestinationType()).isEqualTo("kafka");
  }
}
