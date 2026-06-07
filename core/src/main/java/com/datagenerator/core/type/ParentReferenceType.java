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
 * Represents a reference to a field on the immediately enclosing parent record.
 *
 * <p><b>Syntax:</b> {@code ref[parent.id]} — copies the value of the {@code id} field from the
 * parent record that is currently being generated.
 *
 * <p>Unlike {@link ReferenceType}, which samples a random value from an explicit pool, this type
 * propagates the actual generated value from the parent, guaranteeing referential integrity without
 * requiring a separate pass or lookup.
 *
 * <p><b>Constraint:</b> The referenced parent field must be declared before any nested array or
 * object field that uses {@code ref[parent.*]}, so it is already in the partial record when child
 * generation begins.
 */
@Value
public class ParentReferenceType implements DataType {

  /** The field name to copy from the enclosing parent record (e.g. {@code "id"}). */
  String targetField;

  @Override
  public String describe() {
    return "ref[parent." + targetField + "]";
  }
}
