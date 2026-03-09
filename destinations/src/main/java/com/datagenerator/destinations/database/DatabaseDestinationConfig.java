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

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for the database destination.
 *
 * <p><b>Table name resolution:</b> {@code tableName} defaults to the structure name if not
 * explicitly set. Use the {@code table} key in job YAML to override:
 *
 * <pre>
 * conf:
 *   jdbc_url: "jdbc:postgresql://localhost:5432/testdb"
 *   username: "user"
 *   password: "${DB_PASSWORD}"   # resolved from environment variable
 *   table: "tbl_customers"       # optional — overrides structure name as table
 *   batch_size: 1000
 *   pool_size: 10
 *   transaction_strategy: per_batch
 * </pre>
 *
 * <p><b>Environment variable substitution:</b> String values matching {@code ${VAR_NAME}} are
 * resolved from environment variables at config parse time. If the variable is not set, an
 * exception is thrown at startup.
 *
 * <p><b>Transaction strategies:</b>
 *
 * <ul>
 *   <li>{@code per_batch} — commit after each batch (default, recommended)
 *   <li>{@code per_job} — single commit at end of job (all-or-nothing)
 *   <li>{@code auto_commit} — rely on JDBC auto-commit (no explicit transaction management)
 * </ul>
 */
@Value
@Builder
public class DatabaseDestinationConfig {

  /** JDBC connection URL (e.g. {@code jdbc:postgresql://localhost:5432/mydb}). */
  String jdbcUrl;

  /** Database username. */
  String username;

  /** Database password. Supports {@code ${ENV_VAR}} substitution. */
  String password;

  /**
   * Target table name. Defaults to the structure name (resolved by the caller). Use this to
   * override when the structure name does not match the actual table name.
   */
  String tableName;

  /**
   * Number of records per batch INSERT. Higher values improve throughput but increase memory usage
   * and transaction size. Default: 1000.
   */
  @Builder.Default int batchSize = 1000;

  /**
   * HikariCP maximum pool size. For single-writer use cases (the default), 1–5 is sufficient.
   * Default: 5.
   */
  @Builder.Default int poolSize = 5;

  /**
   * Transaction commit strategy: {@code per_batch}, {@code per_job}, or {@code auto_commit}.
   * Default: {@code per_batch}.
   */
  @Builder.Default String transactionStrategy = "per_batch";
}
