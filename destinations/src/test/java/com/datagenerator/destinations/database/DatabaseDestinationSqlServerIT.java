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

import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration tests for {@link DatabaseDestination} against a real SQL Server instance.
 *
 * <p>Tagged {@code @slow}: the SQL Server image is large and slow to start, so this runs under the
 * {@code slowTest} Gradle task rather than the default {@code integrationTest} task. The portable
 * passport DDL (VARCHAR/DATE/INT, unquoted identifiers) maps cleanly onto T-SQL.
 *
 * <p>{@link MSSQLServerContainer#acceptLicense()} is required by the image's EULA.
 */
@Tag("slow")
class DatabaseDestinationSqlServerIT extends AbstractDatabaseDestinationIT {

  @Container
  static MSSQLServerContainer<?> sqlServer =
      new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

  @Override
  protected JdbcDatabaseContainer<?> container() {
    return sqlServer;
  }
}
