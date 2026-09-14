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

import com.datagenerator.cli.ExecuteCommand.ResolvedSasl;
import org.junit.jupiter.api.Test;

/** Unit tests for Kafka SASL JAAS synthesis from username/password (issue #292, option 1). */
class KafkaSaslConfigTest {

  @Test
  void shouldSynthesizePlainJaasFromUsernamePassword() {
    ResolvedSasl r = ExecuteCommand.resolveSasl("SASL_SSL", "PLAIN", null, "alice", "s3cret");

    assertThat(r.mechanism()).isEqualTo("PLAIN");
    assertThat(r.jaasConfig())
        .isEqualTo(
            "org.apache.kafka.common.security.plain.PlainLoginModule required"
                + " username=\"alice\" password=\"s3cret\";");
  }

  @Test
  void shouldSynthesizeScramJaasForBothScramVariants() {
    for (String mech : new String[] {"SCRAM-SHA-256", "SCRAM-SHA-512"}) {
      ResolvedSasl r = ExecuteCommand.resolveSasl("SASL_SSL", mech, null, "u", "p");
      assertThat(r.mechanism()).isEqualTo(mech);
      assertThat(r.jaasConfig())
          .startsWith("org.apache.kafka.common.security.scram.ScramLoginModule required")
          .contains("username=\"u\"")
          .contains("password=\"p\"");
    }
  }

  @Test
  void shouldDefaultToPlainWhenMechanismOmittedButCredentialsGiven() {
    ResolvedSasl r = ExecuteCommand.resolveSasl("SASL_PLAINTEXT", null, null, "u", "p");

    assertThat(r.mechanism()).isEqualTo("PLAIN");
    assertThat(r.jaasConfig()).contains("PlainLoginModule");
  }

  @Test
  void shouldEscapeQuotesAndBackslashesInCredentials() {
    ResolvedSasl r = ExecuteCommand.resolveSasl("SASL_SSL", "PLAIN", null, "a\"b\\c", "p\"w\\d");

    // " -> \"   and   \ -> \\
    assertThat(r.jaasConfig())
        .contains("username=\"a\\\"b\\\\c\"")
        .contains("password=\"p\\\"w\\\\d\"");
  }

  @Test
  void shouldLetExplicitJaasConfigTakePrecedenceOverCredentials() {
    String explicit = "org.example.CustomLoginModule required token=\"abc\";";
    ResolvedSasl r = ExecuteCommand.resolveSasl("SASL_SSL", "SCRAM-SHA-512", explicit, "u", "p");

    assertThat(r.jaasConfig()).isEqualTo(explicit);
    assertThat(r.mechanism()).isEqualTo("SCRAM-SHA-512");
  }

  @Test
  void shouldPassThroughWhenNoCredentials() {
    ResolvedSasl r = ExecuteCommand.resolveSasl(null, null, null, null, null);
    assertThat(r.mechanism()).isNull();
    assertThat(r.jaasConfig()).isNull();
  }

  @Test
  void shouldFailWhenOnlyOneOfUsernamePasswordProvided() {
    assertThatThrownBy(() -> ExecuteCommand.resolveSasl("SASL_SSL", "PLAIN", null, "u", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("both 'username' and 'password' are required");
    assertThatThrownBy(() -> ExecuteCommand.resolveSasl("SASL_SSL", "PLAIN", null, null, "p"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldFailWhenSecurityProtocolMissingWithCredentials() {
    assertThatThrownBy(() -> ExecuteCommand.resolveSasl(null, "PLAIN", null, "u", "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'security_protocol' is required");
  }

  @Test
  void shouldFailForNonUserPasswordMechanism() {
    assertThatThrownBy(() -> ExecuteCommand.resolveSasl("SASL_SSL", "GSSAPI", null, "u", "p"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GSSAPI");
  }
}
