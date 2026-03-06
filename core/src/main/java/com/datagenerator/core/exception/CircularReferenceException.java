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

package com.datagenerator.core.exception;

/**
 * Thrown when a circular reference is detected in nested object structures.
 *
 * <p>This exception occurs when the {@link com.datagenerator.core.structure.StructureRegistry}
 * detects a cycle in object type references that would cause infinite recursion during generation.
 *
 * <p><b>Common Causes:</b>
 *
 * <ul>
 *   <li><b>Direct cycle:</b> Structure A contains {@code object[A]}
 *   <li><b>Indirect cycle:</b> Structure A contains {@code object[B]}, B contains {@code object[A]}
 *   <li><b>Multi-level cycle:</b> A → B → C → A
 * </ul>
 *
 * <p><b>Example Problem:</b>
 *
 * <pre>
 * # user.yaml
 * name: user
 * data:
 *   user_id: uuid
 *   manager: object[user]  # Direct cycle!
 *
 * # organization.yaml
 * name: organization
 * data:
 *   org_id: uuid
 *   parent: object[department]
 *
 * # department.yaml
 * name: department
 * data:
 *   dept_id: uuid
 *   org: object[organization]  # Cycle: organization ↔ department
 * </pre>
 *
 * <p><b>Resolution Strategies:</b>
 *
 * <ol>
 *   <li><b>Use foreign keys instead of nested objects:</b> Replace {@code object[user]} with {@code
 *       ref[user.user_id]} or {@code uuid} to reference by ID
 *   <li><b>Use arrays for one-to-many:</b> Instead of bidirectional nesting, use {@code
 *       array[object[child], 0..5]} in parent only
 *   <li><b>Flatten structure:</b> Generate parent and child as separate structures, link via IDs
 *   <li><b>Optional nesting:</b> Use boolean flag to conditionally include nested object
 * </ol>
 *
 * <p><b>Corrected Example:</b>
 *
 * <pre>
 * # user.yaml (using foreign key instead of nested object)
 * name: user
 * data:
 *   user_id: uuid
 *   manager_id: uuid  # Foreign key to another user - no cycle!
 * </pre>
 *
 * <p><b>Detection:</b> Circular references are detected at structure loading time before generation
 * begins, preventing infinite recursion and stack overflow errors.
 *
 * @see com.datagenerator.core.structure.StructureRegistry
 * @see com.datagenerator.core.type.ObjectType
 * @since 1.0
 */
public class CircularReferenceException extends RuntimeException {
  /**
   * Constructs a new circular reference exception with the specified detail message.
   *
   * <p>The message typically includes the cycle path, e.g., "Circular reference detected: user →
   * manager → user"
   *
   * @param message the detail message showing the reference cycle
   */
  public CircularReferenceException(String message) {
    super(message);
  }

  /**
   * Constructs a new circular reference exception with the specified detail message and cause.
   *
   * @param message the detail message showing the reference cycle
   * @param cause the underlying cause (if any)
   */
  public CircularReferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
