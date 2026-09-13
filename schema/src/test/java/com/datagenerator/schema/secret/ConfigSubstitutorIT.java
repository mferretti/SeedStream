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

package com.datagenerator.schema.secret;

import static org.assertj.core.api.Assertions.assertThat;

import com.datagenerator.schema.IntegrationTest;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

class ConfigSubstitutorIT extends IntegrationTest {

  private static final String VAULT_TOKEN = "test-root-token";

  @SuppressWarnings("java:S2068")
  @Container
  static VaultContainer<?> vault =
      new VaultContainer<>(DockerImageName.parse("hashicorp/vault:1.17.3"))
          .withVaultToken(VAULT_TOKEN)
          .withInitCommand("kv put secret/app password=vault-secret-value");

  /**
   * Resolver pointed at the test container with the token injected via the env-reader hook, so the
   * IT needs no externally exported {@code VAULT_TOKEN} (a JVM cannot set its own {@code getenv}).
   */
  private static VaultSecretResolver resolver() {
    return new VaultSecretResolver(
        vault.getHttpHostAddress(),
        null,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        key -> VAULT_TOKEN);
  }

  @Test
  void shouldSubstituteSecretPatternViaVaultResolver() {
    String result = ConfigSubstitutor.substitute("${SECRET:secret/data/app#password}", resolver());
    assertThat(result).isEqualTo("vault-secret-value");
  }

  @Test
  void shouldReturnLiteralValueUnchanged() {
    assertThat(ConfigSubstitutor.substitute("plain-value", resolver())).isEqualTo("plain-value");
  }

  @Test
  void shouldReturnNullForNullInput() {
    assertThat(ConfigSubstitutor.substitute(null, resolver())).isNull();
  }
}
