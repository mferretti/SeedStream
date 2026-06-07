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
 *
 * <p><b>Example — Encrypted file (AES-256-GCM):</b>
 *
 * <pre>
 * secrets:
 *   resolver: encrypted_file
 *   key_env: SEEDSTREAM_ENCRYPTION_KEY   # optional; this is the default
 *
 * conf:
 *   password: "${SECRET:enc:AES256GCM:BASE64CIPHERTEXT...}"
 * </pre>
 */
@Value
public class SecretsConfig {

  /**
   * Resolver backend. Supported values: {@code env} (default), {@code vault}, {@code aws}, {@code
   * azure_keyvault}, {@code gcp_secretmanager}, {@code encrypted_file}.
   */
  String resolver;

  /**
   * Vault server address (e.g. {@code https://vault.example.com:8200}). Required for {@code vault}
   * resolver.
   */
  String vaultAddr;

  /** Optional Vault namespace header ({@code X-Vault-Namespace}). */
  String vaultNamespace;

  /**
   * AWS region for the Secrets Manager endpoint (e.g. {@code us-east-1}). Optional; falls back to
   * {@code AWS_DEFAULT_REGION} environment variable when absent.
   */
  String awsRegion;

  /**
   * Azure Key Vault URI (e.g. {@code https://myvault.vault.azure.net}). Required for {@code
   * azure_keyvault} resolver.
   */
  String vaultUri;

  /**
   * Environment variable name holding the 64-char hex AES-256 key for the {@code encrypted_file}
   * resolver. Defaults to {@code SEEDSTREAM_ENCRYPTION_KEY} when absent.
   */
  String keyEnv;

  /**
   * Path to a file containing the 64-char hex AES-256 key for the {@code encrypted_file} resolver.
   * Takes precedence over {@code key_env} when both are set.
   */
  String keyFile;

  /**
   * GCP project ID for Google Cloud Secret Manager (e.g. {@code my-gcp-project}). Required for
   * {@code gcp_secretmanager} resolver.
   */
  String gcpProjectId;

  @JsonCreator
  @SuppressWarnings("checkstyle:HiddenField") // @JsonCreator requires params named after fields
  public SecretsConfig(
      @JsonProperty("resolver") String resolver,
      @JsonProperty("vault_addr") String vaultAddr,
      @JsonProperty("vault_namespace") String vaultNamespace,
      @JsonProperty("aws_region") String awsRegion,
      @JsonProperty("vault_uri") String vaultUri,
      @JsonProperty("key_env") String keyEnv,
      @JsonProperty("key_file") String keyFile,
      @JsonProperty("gcp_project_id") String gcpProjectId) {
    this.resolver = resolver;
    this.vaultAddr = vaultAddr;
    this.vaultNamespace = vaultNamespace;
    this.awsRegion = awsRegion;
    this.vaultUri = vaultUri;
    this.keyEnv = keyEnv;
    this.keyFile = keyFile;
    this.gcpProjectId = gcpProjectId;
  }
}
