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
import lombok.ToString;
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
 *   truncate_before_insert: true # DESTRUCTIVE — empties the table (CASCADE) before seeding
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
  // Excluded from toString() to keep the credential out of logs / exception messages (CWE-532).
  @ToString.Exclude String password;

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

  /** Maximum connection attempts during {@code open()}. Default: 3. Set to 1 to disable retries. */
  @Builder.Default int maxRetries = 3;

  /**
   * Initial delay in milliseconds between connection retry attempts. Doubles on each retry.
   * Default: 1000.
   */
  @Builder.Default long retryDelayMs = 1000;

  /**
   * When {@code true} (default), the nested-record decomposer automatically injects a {@code
   * {parent_table}_id} FK column into each child record. Set to {@code false} when child structures
   * use {@code ref[parent.field]} to populate FK fields explicitly, avoiding a redundant second
   * column.
   */
  @Builder.Default boolean injectParentFk = true;

  /**
   * When {@code true}, each target table is emptied with {@code TRUNCATE TABLE ... CASCADE} before
   * its first insert. Default: {@code false}.
   *
   * <p><b>DESTRUCTIVE</b> — intended for CI seeding into a disposable database, where a fixed seed
   * plus a clean table yields a deterministic dataset without an external teardown script. The
   * table must already exist (no DDL generation). Uses PostgreSQL/Oracle {@code CASCADE} syntax, so
   * nested foreign-key graphs are cleared in one shot; MySQL/SQL Server are not supported.
   */
  @Builder.Default boolean truncateBeforeInsert = false;
}
