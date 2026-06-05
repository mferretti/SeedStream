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

package com.datagenerator.schema.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Configuration for the {@code secrets:} block in a job YAML file.
 *
 * <p>Controls how {@code ${SECRET:path}} references in configuration values are resolved. When
 * absent, {@code ${SECRET:path}} falls back to environment variable resolution.
 *
 * <p><b>Example — HashiCorp Vault:</b>
 *
 * <pre>
 * secrets:
 *   resolver: vault
 *   vault_addr: https://vault.example.com:8200
 *   vault_namespace: myteam   # optional
 *
 * conf:
 *   password: "${SECRET:secret/data/myapp/db#password}"
 * </pre>
 *
 * <p><b>Vault token:</b> Always read from the {@code VAULT_TOKEN} environment variable — never
 * stored in the job YAML.
 */
@Value
public class SecretsConfig {

  /** Resolver backend. Supported values: {@code env} (default), {@code vault}. */
  String resolver;

  /**
   * Vault server address (e.g. {@code https://vault.example.com:8200}). Required for {@code vault}
   * resolver.
   */
  String vaultAddr;

  /** Optional Vault namespace header ({@code X-Vault-Namespace}). */
  String vaultNamespace;

  @JsonCreator
  public SecretsConfig(
      @JsonProperty("resolver") String resolver,
      @JsonProperty("vault_addr") String vaultAddr,
      @JsonProperty("vault_namespace") String vaultNamespace) {
    this.resolver = resolver;
    this.vaultAddr = vaultAddr;
    this.vaultNamespace = vaultNamespace;
  }
}
