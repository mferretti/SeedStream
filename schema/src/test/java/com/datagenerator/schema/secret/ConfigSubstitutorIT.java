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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

class ConfigSubstitutorIT extends IntegrationTest {

  private static final String VAULT_TOKEN = "test-root-token";

  @Container
  static VaultContainer<?> vault =
      new VaultContainer<>(DockerImageName.parse("hashicorp/vault:1.17.3"))
          .withVaultToken(VAULT_TOKEN)
          .withInitCommand("kv put secret/app password=vault-secret-value");

  @BeforeEach
  void setToken() {
    System.setProperty("VAULT_TOKEN", VAULT_TOKEN);
  }

  @AfterEach
  void clearToken() {
    System.clearProperty("VAULT_TOKEN");
  }

  @Test
  void shouldSubstituteSecretPatternViaVaultResolver() {
    VaultSecretResolver resolver = new VaultSecretResolver(vault.getHttpHostAddress(), null);
    String result = ConfigSubstitutor.substitute("${SECRET:secret/data/app#password}", resolver);
    assertThat(result).isEqualTo("vault-secret-value");
  }

  @Test
  void shouldReturnLiteralValueUnchanged() {
    VaultSecretResolver resolver = new VaultSecretResolver(vault.getHttpHostAddress(), null);
    assertThat(ConfigSubstitutor.substitute("plain-value", resolver)).isEqualTo("plain-value");
  }

  @Test
  void shouldReturnNullForNullInput() {
    VaultSecretResolver resolver = new VaultSecretResolver(vault.getHttpHostAddress(), null);
    assertThat(ConfigSubstitutor.substitute(null, resolver)).isNull();
  }
}
