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

import org.junit.jupiter.api.Test;

class KafkaDestinationConfigTest {

  @Test
  void shouldExcludeSecretsFromToString() {
    // D1 / CWE-532: SASL JAAS string and SSL passwords must not appear in toString().
    KafkaDestinationConfig config =
        KafkaDestinationConfig.builder()
            .bootstrap("broker:9092")
            .topic("events")
            .saslJaasConfig(
                "org.apache.kafka.common.security.plain.PlainLoginModule required"
                    + " username=\"u\" password=\"jaas-secret\";")
            .sslTruststorePassword("truststore-secret")
            .sslKeystorePassword("keystore-secret")
            .build();

    String rendered = config.toString();

    assertThat(rendered)
        .doesNotContain("jaas-secret")
        .doesNotContain("truststore-secret")
        .doesNotContain("keystore-secret")
        .contains("events"); // non-secret fields kept
  }
}
