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

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

class DatabaseDestinationMySqlIT extends AbstractDatabaseDestinationIT {
  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.4")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  @Override
  protected JdbcDatabaseContainer<?> container() {
    return mysql;
  }

  // MySQL Connector/J applies the session time zone to DATE retrieval, which can
  // shift getDate() by a day. Pin UTC so DATE round-trips match LocalDate exactly.
  @Override
  protected String jdbcUrl() {
    String base = container().getJdbcUrl();
    return base + (base.contains("?") ? "&" : "?") + "connectionTimeZone=UTC";
  }
}
