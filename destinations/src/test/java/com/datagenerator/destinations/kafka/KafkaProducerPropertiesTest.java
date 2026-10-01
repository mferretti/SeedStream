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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.formats.json.JsonSerializer;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/**
 * Verifies the exact producer {@link Properties} built by {@code KafkaDestination.open()}. The
 * builder is private, so the narrowest seam is intercepting the {@link KafkaProducer} constructor.
 */
class KafkaProducerPropertiesTest {

  // Fake credential, test only.
  private static final String SECRET = "s3cr3t-value"; // nosemgrep
  private static final String BOOTSTRAP = "b1:9092,b2:9092";
  private static final String IDEMPOTENCE = "enable.idempotence";
  // Kafka property names, not secrets.
  private static final String TS_PASSWORD = "ssl.truststore.password"; // nosemgrep
  private static final String KS_PASSWORD = "ssl.keystore.password"; // nosemgrep

  private static KafkaDestinationConfig.KafkaDestinationConfigBuilder base() {
    return KafkaDestinationConfig.builder().bootstrap(BOOTSTRAP).topic("t");
  }

  private Properties props(KafkaDestinationConfig config) {
    Properties[] holder = new Properties[1];
    try (MockedConstruction<KafkaProducer> ignored =
        Mockito.mockConstruction(
            KafkaProducer.class, (mock, ctx) -> holder[0] = (Properties) ctx.arguments().get(0))) {
      new KafkaDestination(config, new JsonSerializer()).open();
    }
    assertThat(holder[0]).as("producer constructed with Properties").isNotNull();
    return holder[0];
  }

  @Test
  void shouldApplyDefaultsWhenOptionalConfigAbsent() {
    Properties p = props(base().build());

    assertThat(p)
        .containsEntry("bootstrap.servers", BOOTSTRAP)
        .containsEntry("acks", "all")
        .containsEntry(IDEMPOTENCE, "true")
        .containsEntry("compression.type", "none")
        .containsEntry("batch.size", 16384)
        .containsEntry("linger.ms", 10)
        .containsEntry("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .containsEntry(
            "value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    assertThat(p.stringPropertyNames())
        .doesNotContain(
            "security.protocol",
            "sasl.mechanism",
            "sasl.jaas.config",
            "ssl.truststore.location",
            TS_PASSWORD,
            "ssl.keystore.location",
            KS_PASSWORD);
  }

  @Test
  void shouldApplyExplicitTuningValues() {
    Properties p = props(base().batchSize(65536).lingerMs(250).compression("zstd").build());

    assertThat(p)
        .containsEntry("batch.size", 65536)
        .containsEntry("linger.ms", 250)
        .containsEntry("compression.type", "zstd");
  }

  @Test
  void shouldEnableIdempotenceWhenAcksIsMinusOne() {
    Properties p = props(base().acks("-1").build());

    assertThat(p).containsEntry("acks", "-1").containsEntry(IDEMPOTENCE, "true");
  }

  @Test
  void shouldNotSetIdempotenceWhenAcksIsOne() {
    Properties p = props(base().acks("1").build());

    assertThat(p).containsEntry("acks", "1");
    assertThat(p.containsKey(IDEMPOTENCE)).isFalse();
  }

  @Test
  void shouldNotSetIdempotenceWhenAcksIsZero() {
    Properties p = props(base().acks("0").build());

    assertThat(p).containsEntry("acks", "0");
    assertThat(p.containsKey(IDEMPOTENCE)).isFalse();
  }

  @Test
  void shouldPassSaslSettingsVerbatim() {
    String jaas =
        "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\"u\" password=\""
            + SECRET
            + "\";";
    Properties p =
        props(
            base()
                .securityProtocol("SASL_SSL")
                .saslMechanism("SCRAM-SHA-512")
                .saslJaasConfig(jaas)
                .build());

    assertThat(p)
        .containsEntry("security.protocol", "SASL_SSL")
        .containsEntry("sasl.mechanism", "SCRAM-SHA-512")
        .containsEntry("sasl.jaas.config", jaas);
  }

  @Test
  void shouldPassSslStoresAndPasswords() {
    Properties p =
        props(
            base()
                .securityProtocol("SSL")
                .sslTruststoreLocation("/etc/ts.jks")
                .sslTruststorePassword("tspw")
                .sslKeystoreLocation("/etc/ks.jks")
                .sslKeystorePassword("kspw")
                .build());

    assertThat(p)
        .containsEntry("ssl.truststore.location", "/etc/ts.jks")
        .containsEntry(TS_PASSWORD, "tspw")
        .containsEntry("ssl.keystore.location", "/etc/ks.jks")
        .containsEntry(KS_PASSWORD, "kspw");
  }

  @Test
  void shouldOmitStorePasswordsWhenLocationAbsent() {
    Properties p = props(base().sslTruststorePassword("tspw").sslKeystorePassword("kspw").build());

    assertThat(p.stringPropertyNames()).isNotEmpty().doesNotContain(TS_PASSWORD, KS_PASSWORD);
  }

  @Test
  void shouldOmitPasswordsWhenOnlyStoreLocationsGiven() {
    Properties p = props(base().sslTruststoreLocation("/ts").sslKeystoreLocation("/ks").build());

    assertThat(p)
        .containsEntry("ssl.truststore.location", "/ts")
        .containsEntry("ssl.keystore.location", "/ks");
    assertThat(p.stringPropertyNames()).isNotEmpty().doesNotContain(TS_PASSWORD, KS_PASSWORD);
  }

  @Test
  void shouldNotLeakSecretsInConfigToString() {
    String text =
        base()
            .saslJaasConfig("login password=\"" + SECRET + "\"")
            .sslTruststorePassword(SECRET)
            .sslKeystorePassword(SECRET)
            .build()
            .toString();

    assertThat(text).doesNotContain(SECRET).contains(BOOTSTRAP);
  }
}
