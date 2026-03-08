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

/**
 * Base class for all data types in the generation system. Each type knows how to validate and
 * describe itself.
 */
public sealed interface DataType
    permits PrimitiveType, EnumType, ObjectType, ArrayType, ReferenceType, CustomDatafakerType {
  /**
   * Returns a human-readable description of this type (e.g., "char[3..15]", "array[int, 5..10]").
   *
   * @return human-readable type description
   */
  String describe();
}
