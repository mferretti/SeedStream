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

/**
 * Resolves a secret path to its plaintext value.
 *
 * <p>Implementations include {@link EnvSecretResolver} (environment variables) and {@link
 * VaultSecretResolver} (HashiCorp Vault KV v2). Additional backends can be added without changing
 * calling code.
 *
 * <p>Implementations must never log resolved values. Resolved values must not be stored beyond the
 * immediate use in configuration setup.
 *
 * @see ConfigSubstitutor
 * @see SecretResolverFactory
 */
@FunctionalInterface
public interface SecretResolver {

  /**
   * Resolve a secret path to its plaintext value.
   *
   * @param path backend-specific path (env var name, Vault KV path, etc.)
   * @return the resolved plaintext value; never null
   * @throws SecretResolutionException if the secret cannot be resolved
   */
  String resolve(String path);
}
