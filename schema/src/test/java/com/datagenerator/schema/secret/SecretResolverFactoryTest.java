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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretResolverFactoryTest {

  private static final String TYPE_VAULT = "vault";
  private static final String TYPE_AZURE = "azure_keyvault";
  private static final String TYPE_ENCRYPTED = "encrypted_file";

  @Test
  void shouldReturnEnvResolverWhenConfigIsNull() {
    assertThat(SecretResolverFactory.create(null)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"env", "ENV"})
  void shouldReturnEnvResolverForNullOrEnvType(String type) {
    SecretsConfig config = new SecretsConfig(type, null, null, null, null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isSameAs(EnvSecretResolver.INSTANCE);
  }

  @Test
  void shouldReturnVaultResolverWhenVaultTypeWithAddr() {
    SecretsConfig config =
        new SecretsConfig(
            TYPE_VAULT, "https://vault.example.com:8200", null, null, null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(VaultSecretResolver.class);
  }

  @Test
  void shouldReturnVaultResolverWithNamespace() {
    SecretsConfig config =
        new SecretsConfig(
            TYPE_VAULT, "https://vault.example.com:8200", "myteam", null, null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(VaultSecretResolver.class);
  }

  @Test
  void shouldThrowWhenVaultAddrMissingForVaultType() {
    SecretsConfig config = new SecretsConfig(TYPE_VAULT, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_addr");
  }

  @Test
  void shouldThrowWhenVaultAddrBlankForVaultType() {
    SecretsConfig config = new SecretsConfig(TYPE_VAULT, "   ", null, null, null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_addr");
  }

  @Test
  void shouldReturnAwsResolverForAwsType() {
    // aws_region provided so the SDK can construct the client without querying AWS metadata
    SecretsConfig config =
        new SecretsConfig("aws", null, null, "us-east-1", null, null, null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(AwsSecretsManagerResolver.class);
  }

  @Test
  void shouldReturnAzureKeyVaultResolverForAzureType() {
    SecretsConfig config =
        new SecretsConfig(
            TYPE_AZURE, null, null, null, "https://myvault.vault.azure.net", null, null, null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(AzureKeyVaultResolver.class);
  }

  @Test
  void shouldThrowWhenVaultUriMissingForAzureType() {
    SecretsConfig config = new SecretsConfig(TYPE_AZURE, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  @Test
  void shouldThrowWhenVaultUriBlankForAzureType() {
    SecretsConfig config = new SecretsConfig(TYPE_AZURE, null, null, null, "   ", null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("vault_uri");
  }

  @Test
  void shouldThrowWhenGcpProjectIdMissingForGcpType() {
    SecretsConfig config =
        new SecretsConfig("gcp_secretmanager", null, null, null, null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("gcp_project_id");
  }

  @Test
  void shouldThrowWhenGcpProjectIdBlankForGcpType() {
    SecretsConfig config =
        new SecretsConfig("gcp_secretmanager", null, null, null, null, null, null, "   ");
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("gcp_project_id");
  }

  @Test
  void shouldReturnEncryptedFileResolverFromKeyFile(@TempDir Path tempDir) throws IOException {
    // generate a valid 64-char hex key (32 zero bytes)
    String keyHex = "0".repeat(64);
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, keyHex);

    SecretsConfig config =
        new SecretsConfig(TYPE_ENCRYPTED, null, null, null, null, null, keyFile.toString(), null);
    assertThat(SecretResolverFactory.create(config)).isInstanceOf(EncryptedFileResolver.class);
  }

  @Test
  void shouldThrowWhenEncryptedFileKeyEnvNotSet() {
    SecretsConfig config =
        new SecretsConfig(
            TYPE_ENCRYPTED, null, null, null, null, "NONEXISTENT_ENC_KEY_XYZ_12345", null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("NONEXISTENT_ENC_KEY_XYZ_12345");
  }

  @Test
  void shouldThrowWhenEncryptedFileKeyFileNotFound() {
    SecretsConfig config =
        new SecretsConfig(
            TYPE_ENCRYPTED, null, null, null, null, null, "/nonexistent/enc/key.hex", null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("/nonexistent/enc/key.hex");
  }

  @Test
  void shouldRoundTripEncryptedValueViaFactory(@TempDir Path tempDir) throws IOException {
    byte[] key = new byte[32]; // all-zero 32-byte key → hex "00" * 32
    Path keyFile = tempDir.resolve("key.hex");
    Files.writeString(keyFile, "0".repeat(64));

    String plaintext = "super-secret-value";
    String ciphertext = AesGcmCrypto.encrypt(key, plaintext);
    String encPath =
        EncryptedFileResolver.ENC_PREFIX + ciphertext.substring(AesGcmCrypto.PREFIX.length());

    SecretsConfig config =
        new SecretsConfig(TYPE_ENCRYPTED, null, null, null, null, null, keyFile.toString(), null);
    SecretResolver resolver = SecretResolverFactory.create(config);

    assertThat(resolver.resolve(encPath)).isEqualTo(plaintext);
  }

  @Test
  void shouldThrowForUnknownResolverType() {
    SecretsConfig config =
        new SecretsConfig("unknown_backend", null, null, null, null, null, null, null);
    assertThatThrownBy(() -> SecretResolverFactory.create(config))
        .isInstanceOf(SecretResolutionException.class)
        .hasMessageContaining("unknown_backend")
        .hasMessageContaining(TYPE_ENCRYPTED);
  }
}
