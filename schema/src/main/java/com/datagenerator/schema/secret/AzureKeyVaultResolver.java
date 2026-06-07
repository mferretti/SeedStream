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

import com.azure.core.exception.AzureException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.datagenerator.schema.exception.SecretResolutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves secrets from Azure Key Vault.
 *
 * <p><b>Path syntax:</b> Secret name, or {@code name/version} to pin a specific version. Examples:
 *
 * <ul>
 *   <li>{@code my-db-password} — latest version of the secret
 *   <li>{@code my-db-password/abc123def456} — specific version
 * </ul>
 *
 * <p><b>Authentication:</b> DefaultAzureCredential chain (environment variables → workload identity
 * → managed identity → Azure CLI → Azure PowerShell). Credentials are never read from job YAML.
 *
 * <p><b>Vault URI:</b> From {@code vault_uri} config field (e.g. {@code
 * https://myvault.vault.azure.net}). Required.
 *
 * <p><b>Security:</b> Resolved values are never logged.
 */
@Slf4j
public final class AzureKeyVaultResolver implements SecretResolver {

  private final SecretClient client;

  public AzureKeyVaultResolver(String vaultUri) {
    if (vaultUri == null || vaultUri.isBlank()) {
      throw new SecretResolutionException(
          "vault_uri is required when secrets.resolver: azure_keyvault");
    }
    this.client =
        new SecretClientBuilder()
            .vaultUrl(vaultUri)
            .credential(new DefaultAzureCredentialBuilder().build())
            .buildClient();
  }

  /** Package-private constructor for injecting a mock client in unit tests. */
  AzureKeyVaultResolver(SecretClient kvClient) {
    this.client = kvClient;
  }

  @Override
  public String resolve(String path) {
    String secretName = path;
    String version = null;
    int slash = path.indexOf('/');
    if (slash >= 0) {
      secretName = path.substring(0, slash);
      version = path.substring(slash + 1);
    }

    log.debug("Resolving Azure Key Vault secret: name={}", secretName);

    KeyVaultSecret secret;
    try {
      secret =
          (version != null && !version.isBlank())
              ? client.getSecret(secretName, version)
              : client.getSecret(secretName);
    } catch (ResourceNotFoundException e) {
      throw new SecretResolutionException("Azure secret not found: '" + secretName + "'", e);
    } catch (HttpResponseException e) {
      throw new SecretResolutionException(
          "Azure Key Vault error for '" + secretName + "': " + e.getMessage(), e);
    } catch (AzureException e) {
      throw new SecretResolutionException(
          "Azure Key Vault error for '" + secretName + "': " + e.getMessage(), e);
    }

    String value = secret.getValue();
    if (value == null) {
      throw new SecretResolutionException("Azure secret '" + secretName + "' has a null value");
    }
    return value;
  }
}
