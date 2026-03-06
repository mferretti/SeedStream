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

package com.datagenerator.core.structure;

import com.datagenerator.core.type.DataType;
import java.nio.file.Path;
import java.util.Map;

/**
 * Interface for loading structure definitions. Implementations parse structure files (e.g., YAML)
 * and return field definitions.
 */
@FunctionalInterface
public interface StructureLoader {
  /**
   * Load a structure definition from file.
   *
   * @param structureName the name of the structure
   * @param structuresPath the base path for structure files
   * @param registry the registry for resolving nested structures
   * @return map of field names to DataType objects
   */
  Map<String, DataType> load(String structureName, Path structuresPath, StructureRegistry registry);
}
