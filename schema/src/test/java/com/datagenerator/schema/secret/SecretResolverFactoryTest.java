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

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.schema.exception.SecretResolutionException;
import com.datagenerator.schema.model.SecretsConfig;
import org.junit.jupiter.api.Test;

class SecretResolverFactoryTest {

  @Test
  void shouldReturnEnvResolverWhenConfigIsNull() {
    assertThat(SecretResolverFactory.create(null)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @Test
  void shouldReturnEnvResolverWhenResolverTypeIsNull() {
    SecretsConfig config = new SecretsConfig(null, null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @Test
  void shouldReturnEnvResolverForEnvType() {
    SecretsConfig config = new SecretsConfig("env", null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @Test
  void shouldReturnEnvResolverForEnvTypeUpperCase() {
    SecretsConfig config = new SecretsConfig("ENV", null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @Test
  void shouldReturnVaultResolverWhenVaultTypeWithAddr() {
    SecretsConfig config =
        new SecretsConfig("vault", "https://vault.example.com:8200", null, null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(VaultSecretResolver.class);
  }

  @Test
  void shouldReturnVaultResolverWithNamespace() {
    SecretsConfig config =
        new SecretsConfig("vault", "https://vault.example.com:8200", "myteam", null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(VaultSecretResolver.class);
  }

  @Test
  void shouldThrowWhenVaultAddrMissingForVaultType() {
    SecretsConfig config = new SecretsConfig("vault", null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_addr");
  }

  @Test
  void shouldThrowWhenVaultAddrBlankForVaultType() {
    SecretsConfig config = new SecretsConfig("vault", "   ", null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_addr");
  }

  @Test
  void shouldReturnAwsResolverForAwsType() {
    // aws_region provided so the SDK can construct the client without querying AWS metadata
    SecretsConfig config = new SecretsConfig("aws", null, null, "us-east-1", null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(AwsSecretsManagerResolver.class);
  }

  @Test
  void shouldReturnAzureKeyVaultResolverForAzureType() {
    SecretsConfig config =
        new SecretsConfig("azure_keyvault", null, null, null, "https://myvault.vault.azure.net");
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(AzureKeyVaultResolver.class);
  }

  @Test
  void shouldThrowWhenVaultUriMissingForAzureType() {
    SecretsConfig config = new SecretsConfig("azure_keyvault", null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  @Test
  void shouldThrowWhenVaultUriBlankForAzureType() {
    SecretsConfig config = new SecretsConfig("azure_keyvault", null, null, null, "   ");
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  @Test
  void shouldThrowForUnknownResolverType() {
    SecretsConfig config = new SecretsConfig("gcp", null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("gcp")
        .hasMessageContaining("env, vault, aws, azure_keyvault");
  }
}
