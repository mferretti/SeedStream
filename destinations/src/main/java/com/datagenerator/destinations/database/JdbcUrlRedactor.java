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

package com.datagenerator.destinations.database;

/**
 * Redacts credentials from a JDBC URL for safe logging (CWE-532).
 *
 * <p>{@code jdbc_url} is run through {@code ConfigSubstitutor}, so it can contain a resolved secret
 * as URI userinfo ({@code //user:pass@host}) or as a {@code password}/{@code user} query parameter.
 * Both are masked with {@code ****}; the host and database remain visible.
 */
public final class JdbcUrlRedactor {

  private JdbcUrlRedactor() {}

  /**
   * @param url the (possibly secret-bearing) JDBC URL
   * @return the URL with any embedded credentials masked, or {@code null} if {@code url} is null
   */
  public static String redactJdbcCredentials(String url) {
    if (url == null) {
      return null;
    }
    // Mask URI userinfo: scheme://user:pass@host -> scheme://****@host
    String redacted = url.replaceAll("(//)[^/@]*@", "$1****@");
    // Mask sensitive query-parameter values: ?password=... / &user=... etc.
    redacted = redacted.replaceAll("(?i)([?&;](?:password|pwd|user|username)=)[^&;]*", "$1****");
    return redacted;
  }
}
