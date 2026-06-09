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
 * Resolves secrets from environment variables.
 *
 * <p>Used both as the default {@code ${SECRET:VAR_NAME}} resolver when no {@code secrets:} block is
 * configured, and internally by {@link ConfigSubstitutor} for legacy {@code ${VAR_NAME}} syntax.
 *
 * <p>Falls back to {@link System#getProperty} when the environment variable is not set — this
 * allows test code to inject values without needing OS-level env var manipulation.
 */
public enum EnvSecretResolver implements SecretResolver {
  INSTANCE;

  @Override
  public String resolve(String varName) {
    String value = System.getenv(varName);
    if (value == null) {
      value = System.getProperty(varName);
    }
    if (value == null) {
      throw new SecretResolutionException(
          "Environment variable '" + varName + "' is not set (referenced in job config)");
    }
    return value;
  }
}
