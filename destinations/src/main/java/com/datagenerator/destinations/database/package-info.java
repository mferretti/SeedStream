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
 * Database destination adapter for inserting generated records into relational databases via JDBC.
 *
 * <p><b>Stage 1 scope:</b> Flat tables only (primitives, enums, Datafaker string types). Nested
 * objects and arrays are rejected with a clear error. See {@code DATABASE-DESTINATION-PLANNING.md}
 * for the full staging plan.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.destinations.database.DatabaseDestination} — JDBC adapter with
 *       HikariCP pooling and batch inserts
 *   <li>{@link com.datagenerator.destinations.database.DatabaseDestinationConfig} — configuration
 *       model (JDBC URL, credentials, table name, batch size, transaction strategy)
 *   <li>{@link com.datagenerator.destinations.database.JdbcTypeMapper} — maps generated Java values
 *       to PreparedStatement bindings
 * </ul>
 */
package com.datagenerator.destinations.database;
