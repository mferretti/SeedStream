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

package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a foreign key reference to another structure's field. Used to create relationships
 * between generated records by sampling a random ID from an explicit pool range.
 *
 * <p><b>Syntax:</b>
 *
 * <ul>
 *   <li>{@code ref[user.id, 1..1000]} — sample random long from [1, 1000]
 *   <li>{@code ref[user.id, 1..count]} — max resolves to the current job's {@code --count} at
 *       runtime; range tracks parent count automatically without hardcoding
 *   <li>{@code ref[user.id]} — parsed without pool; generation fails at runtime
 * </ul>
 */
@Value
public class ReferenceType implements DataType {
  String targetStructure;
  String targetField;
  Long min;
  Long max;

  /** When true, {@code max} is ignored and the runtime job count is used as the upper bound. */
  boolean maxIsCount;

  @Override
  public String describe() {
    if (min != null) {
      String maxStr = maxIsCount ? "count" : String.valueOf(max);
      return "ref[" + targetStructure + "." + targetField + ", " + min + ".." + maxStr + "]";
    }
    return "ref[" + targetStructure + "." + targetField + "]";
  }
}
