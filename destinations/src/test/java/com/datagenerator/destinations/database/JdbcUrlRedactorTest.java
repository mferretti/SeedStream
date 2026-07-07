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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcUrlRedactorTest {

  @Test
  void shouldRedactCredentialsInJdbcUrl() {
    // C1 / CWE-532: userinfo and password query params must be masked before logging.
    assertThat(JdbcUrlRedactor.redactJdbcCredentials("jdbc:postgresql://user:s3cret@host:5432/db"))
        .isEqualTo("jdbc:postgresql://****@host:5432/db")
        .doesNotContain("s3cret");
    assertThat(
            JdbcUrlRedactor.redactJdbcCredentials(
                "jdbc:mysql://host/db?user=admin&password=s3cret&ssl=true"))
        .doesNotContain("s3cret")
        .doesNotContain("admin")
        .contains("ssl=true");
  }

  @Test
  void shouldLeaveCleanJdbcUrlUnchanged() {
    String clean = "jdbc:postgresql://db-host:5432/testdb";
    assertThat(JdbcUrlRedactor.redactJdbcCredentials(clean)).isEqualTo(clean);
    assertThat(JdbcUrlRedactor.redactJdbcCredentials(null)).isNull();
  }

  @Test
  void shouldRedactRetryFailureMessageForBadPassword() {
    // Regression for T01: RetryPolicy operation-name / exception message must not carry the
    // resolved credential when a connection attempt fails (wrong password is the common case).
    String url = "jdbc:postgresql://host/db?user=u&password=p";
    String redacted = JdbcUrlRedactor.redactJdbcCredentials(url);
    assertThat(redacted).contains("password=****").doesNotContain("=p");
  }
}
