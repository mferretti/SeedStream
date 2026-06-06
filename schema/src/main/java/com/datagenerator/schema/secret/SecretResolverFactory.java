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

import com.datagenerator.schema.exception.SecretResolutionException;
import com.datagenerator.schema.model.SecretsConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Creates a {@link SecretResolver} from a job's {@link SecretsConfig}.
 *
 * <p>Supported resolver types:
 *
 * <ul>
 *   <li>{@code env} — environment variable resolution (default when no {@code secrets:} block)
 *   <li>{@code vault} — HashiCorp Vault KV v2; requires {@code vault_addr} in config
 *   <li>{@code aws} — AWS Secrets Manager; optional {@code aws_region} in config
 *   <li>{@code azure_keyvault} — Azure Key Vault; requires {@code vault_uri} in config
 *   <li>{@code encrypted_file} — AES-256-GCM inline ciphertext; key from {@code key_env} or {@code
 *       key_file}
 * </ul>
 */
public final class SecretResolverFactory {

  private SecretResolverFactory() {}

  private static String loadEncryptionKey(SecretsConfig config) {
    if (config.getKeyFile() != null && !config.getKeyFile().isBlank()) {
      try {
        return Files.readString(Path.of(config.getKeyFile())).trim();
      } catch (IOException e) {
        throw new SecretResolutionException(
            "Cannot read encryption key file '" + config.getKeyFile() + "': " + e.getMessage(), e);
      }
    }
    String envName =
        (config.getKeyEnv() != null && !config.getKeyEnv().isBlank())
            ? config.getKeyEnv()
            : "SEEDSTREAM_ENCRYPTION_KEY";
    String keyHex = System.getenv(envName);
    if (keyHex == null || keyHex.isBlank()) {
      throw new SecretResolutionException(
          "Encryption key not found: env var '" + envName + "' is not set");
    }
    return keyHex;
  }

  /**
   * Create the appropriate {@link SecretResolver} for the given config.
   *
   * @param config parsed {@code secrets:} block from job YAML; {@code null} means env-only
   * @return configured resolver; never {@code null}
   * @throws SecretResolutionException if the config names an unknown resolver or is missing
   *     required fields
   */
  public static SecretResolver create(SecretsConfig config) {
    if (config == null || config.getResolver() == null) {
      return EnvSecretResolver.INSTANCE;
    }
    return switch (config.getResolver().toLowerCase(Locale.ROOT)) {
      case "env" -> EnvSecretResolver.INSTANCE;
      case "vault" -> {
        if (config.getVaultAddr() == null || config.getVaultAddr().isBlank()) {
          throw new SecretResolutionException(
              "vault_addr is required when secrets.resolver: vault");
        }
        yield new VaultSecretResolver(config.getVaultAddr(), config.getVaultNamespace());
      }
      case "aws" -> new AwsSecretsManagerResolver(config.getAwsRegion());
      case "azure_keyvault" -> new AzureKeyVaultResolver(config.getVaultUri());
      case "encrypted_file" -> {
        String keyHex = loadEncryptionKey(config);
        yield new EncryptedFileResolver(AesGcmCrypto.hexToKey(keyHex));
      }
      case null -> EnvSecretResolver.INSTANCE;
      default ->
          throw new SecretResolutionException(
              "Unknown secret resolver: '"
                  + config.getResolver()
                  + "'; supported values: env, vault, aws, azure_keyvault, encrypted_file");
    };
  }
}
