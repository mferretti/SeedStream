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

/**
 * Secret resolution for job configuration values.
 *
 * <p>Provides pluggable resolution of {@code ${...}} patterns in YAML config strings:
 *
 * <ul>
 *   <li>{@link com.datagenerator.schema.secret.SecretResolver} — interface for all backends
 *   <li>{@link com.datagenerator.schema.secret.EnvSecretResolver} — resolves {@code ${VAR_NAME}}
 *       from environment variables (default)
 *   <li>{@link com.datagenerator.schema.secret.VaultSecretResolver} — resolves {@code
 *       ${SECRET:path}} from HashiCorp Vault KV v2
 *   <li>{@link com.datagenerator.schema.secret.ConfigSubstitutor} — entry point for substituting a
 *       single config value
 *   <li>{@link com.datagenerator.schema.secret.SecretResolverFactory} — creates resolver from job
 *       {@code secrets:} block
 * </ul>
 */
package com.datagenerator.schema.secret;
