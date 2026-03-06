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
 * Composite type generators for complex data structures (arrays, nested objects).
 *
 * <p>This package contains generators for composite types that recursively generate inner elements
 * or fields using the {@link com.datagenerator.generators.GeneratorContext}.
 *
 * <p><b>Generators:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.generators.composite.ArrayGenerator} - Variable-length arrays with
 *       inner type generation
 *   <li>{@link com.datagenerator.generators.composite.ObjectGenerator} - Nested structure references
 *       with recursive field generation
 * </ul>
 *
 * <p><b>Recursive Generation:</b> Composite generators delegate to the factory to create inner
 * generators, enabling nested structures like:
 *
 * <pre>
 * array[object[address], 5..10]
 * # Generates array of 5-10 address objects, each with nested fields
 * </pre>
 *
 * <p><b>Circular Reference Prevention:</b> ObjectGenerator uses the {@link
 * com.datagenerator.core.structure.StructureRegistry} which detects and prevents circular references
 * during initialization.
 *
 * <p><b>Thread Safety:</b> Generators are stateless and thread-safe. Context is passed per-call.
 */
package com.datagenerator.generators.composite;
