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
 * Represents a custom Datafaker semantic type registered at runtime via DatafakerRegistry.
 *
 * <p>Unlike {@link PrimitiveType} which uses a fixed enum of types, CustomDatafakerType allows
 * unlimited semantic types to be defined dynamically without code changes.
 *
 * <p><b>Examples:</b>
 *
 * <ul>
 *   <li>Built-in semantic types: "name", "email", "address" (pre-registered)
 *   <li>Custom types: "pokemon", "beer_style", "superhero" (user-registered)
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * // In YAML: datatype: pokemon
 * CustomDatafakerType type = new CustomDatafakerType("pokemon");
 * String value = DatafakerRegistry.generate("pokemon", faker, random);
 * </pre>
 *
 * @see com.datagenerator.core.registry.DatafakerRegistry
 */
@Value
public class CustomDatafakerType implements DataType {
  /** Type name as registered in DatafakerRegistry (e.g., "pokemon", "email"). */
  String typeName;

  @Override
  public String describe() {
    return typeName;
  }
}
