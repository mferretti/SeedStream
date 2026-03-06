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
 * Represents a nested object type that references another structure definition. The structure is
 * loaded from {structuresPath}/{structureName}.yaml.
 */
@Value
public class ObjectType implements DataType {
  String structureName;

  @Override
  public String describe() {
    return "object[" + structureName + "]";
  }
}
