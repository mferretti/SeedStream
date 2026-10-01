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

  private static final String SECRET = "s3cr3t-value"; // nosemgrep: fake test credential
  private static final String BOOTSTRAP = "b1:9092,b2:9092";
  private static final String IDEMPOTENCE = "enable.idempotence";
  private static final String TS_PASSWORD = "ssl.truststore.password"; // nosemgrep: key name
  private static final String KS_PASSWORD = "ssl.keystore.password"; // nosemgrep: key name

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

    assertThat(p.get("bootstrap.servers")).isEqualTo(BOOTSTRAP);
    assertThat(p.get("acks")).isEqualTo("all");
    assertThat(p.get(IDEMPOTENCE)).isEqualTo("true");
    assertThat(p.get("compression.type")).isEqualTo("none");
    assertThat(p.get("batch.size")).isEqualTo(16384);
    assertThat(p.get("linger.ms")).isEqualTo(10);
    assertThat(p.get("key.serializer"))
        .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    assertThat(p.get("value.serializer"))
        .isEqualTo("org.apache.kafka.common.serialization.ByteArraySerializer");
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

    assertThat(p.get("batch.size")).isEqualTo(65536);
    assertThat(p.get("linger.ms")).isEqualTo(250);
    assertThat(p.get("compression.type")).isEqualTo("zstd");
  }

  @Test
  void shouldEnableIdempotenceWhenAcksIsMinusOne() {
    Properties p = props(base().acks("-1").build());

    assertThat(p.get("acks")).isEqualTo("-1");
    assertThat(p.get(IDEMPOTENCE)).isEqualTo("true");
  }

  @Test
  void shouldNotSetIdempotenceWhenAcksIsOne() {
    Properties p = props(base().acks("1").build());

    assertThat(p.get("acks")).isEqualTo("1");
    assertThat(p.containsKey(IDEMPOTENCE)).isFalse();
  }

  @Test
  void shouldNotSetIdempotenceWhenAcksIsZero() {
    Properties p = props(base().acks("0").build());

    assertThat(p.get("acks")).isEqualTo("0");
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

    assertThat(p.get("security.protocol")).isEqualTo("SASL_SSL");
    assertThat(p.get("sasl.mechanism")).isEqualTo("SCRAM-SHA-512");
    assertThat(p.get("sasl.jaas.config")).isEqualTo(jaas);
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

    assertThat(p.get("ssl.truststore.location")).isEqualTo("/etc/ts.jks");
    assertThat(p.get(TS_PASSWORD)).isEqualTo("tspw");
    assertThat(p.get("ssl.keystore.location")).isEqualTo("/etc/ks.jks");
    assertThat(p.get(KS_PASSWORD)).isEqualTo("kspw");
  }

  @Test
  void shouldOmitStorePasswordsWhenLocationAbsent() {
    Properties p = props(base().sslTruststorePassword("tspw").sslKeystorePassword("kspw").build());

    assertThat(p.stringPropertyNames()).doesNotContain(TS_PASSWORD, KS_PASSWORD);
  }

  @Test
  void shouldOmitPasswordsWhenOnlyStoreLocationsGiven() {
    Properties p = props(base().sslTruststoreLocation("/ts").sslKeystoreLocation("/ks").build());

    assertThat(p.get("ssl.truststore.location")).isEqualTo("/ts");
    assertThat(p.get("ssl.keystore.location")).isEqualTo("/ks");
    assertThat(p.stringPropertyNames()).doesNotContain(TS_PASSWORD, KS_PASSWORD);
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
