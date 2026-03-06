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
 * Structure loading and registry for managing nested object references.
 *
 * <p>This package provides infrastructure for loading data structure definitions from YAML files and
 * maintaining a registry of structures for resolving nested object references.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.core.structure.StructureLoader} - Loads structures from filesystem
 *   <li>{@link com.datagenerator.core.structure.StructureRegistry} - Registry with circular
 *       reference detection
 * </ul>
 *
 * <p><b>Circular Reference Detection:</b> The registry detects cycles in object references to
 * prevent infinite recursion during generation. For example:
 *
 * <pre>
 * # user.yaml - INVALID (circular reference)
 * name: user
 * data:
 *   user_id: uuid
 *   manager: object[user]  # ❌ Direct cycle
 *
 * # organization.yaml + department.yaml - INVALID (indirect cycle)
 * # organization.yaml
 * data:
 *   parent: object[department]
 *
 * # department.yaml
 * data:
 *   org: object[organization]  # ❌ Cycle: organization ↔ department
 * </pre>
 *
 * <p><b>Resolution Strategy:</b> Use foreign key references instead of nested objects:
 *
 * <pre>
 * # user.yaml - VALID (using reference instead of nesting)
 * name: user
 * data:
 *   user_id: uuid
 *   manager_id: ref[user.user_id]  # ✅ Reference by ID
 * </pre>
 *
 * <p><b>Thread Safety:</b> StructureRegistry is thread-safe after initialization. Loading should be
 * done before parallel generation begins.
 */
package com.datagenerator.core.structure;
