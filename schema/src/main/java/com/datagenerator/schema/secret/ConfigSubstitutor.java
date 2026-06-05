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

/**
 * Substitutes {@code ${...}} patterns in job config string values.
 *
 * <p>Two patterns are supported:
 *
 * <ul>
 *   <li>{@code ${VAR_NAME}} — resolved from environment variables via {@link EnvSecretResolver}.
 *       This is the existing syntax and always uses the environment regardless of configured
 *       resolver.
 *   <li>{@code ${SECRET:path}} — resolved via the supplied {@link SecretResolver} (e.g. Vault).
 *       Falls back to {@link EnvSecretResolver} when no backend is configured.
 * </ul>
 *
 * <p>Non-matching values are returned unchanged. {@code null} input returns {@code null}.
 */
public final class ConfigSubstitutor {

  private ConfigSubstitutor() {}

  /**
   * Substitute a single config string value.
   *
   * @param value raw config value (may be {@code null}, a literal, or a {@code ${...}} reference)
   * @param secretResolver resolver for {@code ${SECRET:path}} references
   * @return the resolved value, or the original string if no pattern matched
   */
  public static String substitute(String value, SecretResolver secretResolver) {
    if (value == null) {
      return null;
    }
    if (value.startsWith("${SECRET:") && value.endsWith("}")) {
      String path = value.substring(9, value.length() - 1);
      return secretResolver.resolve(path);
    }
    if (value.startsWith("${") && value.endsWith("}")) {
      String varName = value.substring(2, value.length() - 1);
      return EnvSecretResolver.INSTANCE.resolve(varName);
    }
    return value;
  }
}
