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
 * Core exception types for the data generation system.
 *
 * <p>This package defines exceptions related to the core type system, seeding, and structure
 * management. All exceptions extend RuntimeException for cleaner API usage.
 *
 * <p><b>Exception Hierarchy:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.core.exception.TypeParseException} - Type string parsing errors
 *   <li>{@link com.datagenerator.core.exception.CircularReferenceException} - Structure reference
 *       cycles
 *   <li>{@link com.datagenerator.core.exception.SeedResolutionException} - Seed loading failures
 * </ul>
 *
 * <p><b>Error Handling Strategy:</b>
 *
 * <ul>
 *   <li><b>Fail Fast:</b> Configuration errors detected early during parsing/validation
 *   <li><b>Descriptive Messages:</b> Clear error messages with context and resolution hints
 *   <li><b>No Checked Exceptions:</b> All extend RuntimeException to avoid excessive try-catch
 * </ul>
 */
package com.datagenerator.core.exception;
