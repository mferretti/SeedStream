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
 * Carries the parent table name and generated ID for FK injection into child records during nested
 * record decomposition.
 *
 * <p><b>FK column convention:</b> {@code {tableName}_id}. For example, if the parent table is
 * {@code order}, the FK column injected into child records is {@code order_id}. If the parent is
 * referenced via a YAML field named {@code line_items}, the FK column injected into grandchild
 * records is {@code line_items_id}.
 *
 * <p>Tester-created DDL must name FK columns to match this convention.
 *
 * @param tableName the parent table name (or YAML field name for non-root levels)
 * @param parentId the value of the parent's {@code id} field to inject as FK
 */
public record ParentContext(String tableName, Object parentId) {

  /**
   * Returns the FK column name to inject into child records.
   *
   * @return {@code tableName + "_id"}
   */
  public String fkColumnName() {
    return tableName + "_id";
  }
}
