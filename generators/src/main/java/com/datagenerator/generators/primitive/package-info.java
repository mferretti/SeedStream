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
 * Primitive type generators for simple data types with range constraints.
 *
 * <p>This package contains generators for basic data types: strings (char), integers, decimals,
 * booleans, dates, and timestamps. Each generator respects range constraints and produces
 * deterministic values from the provided Random instance.
 *
 * <p><b>Generators:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.generators.primitive.CharGenerator} - Strings with length range
 *   <li>{@link com.datagenerator.generators.primitive.IntegerGenerator} - Integers with numeric
 *       range
 *   <li>{@link com.datagenerator.generators.primitive.DecimalGenerator} - Decimals with numeric
 *       range and precision
 *   <li>{@link com.datagenerator.generators.primitive.BooleanGenerator} - Boolean values (50/50
 *       distribution)
 *   <li>{@link com.datagenerator.generators.primitive.DateGenerator} - Dates within date range
 *   <li>{@link com.datagenerator.generators.primitive.TimestampGenerator} - Timestamps with support
 *       for relative dates (now-30d, now+7d)
 *   <li>{@link com.datagenerator.generators.primitive.EnumGenerator} - Random selection from
 *       predefined values
 * </ul>
 *
 * <p><b>Performance:</b> Primitive generators are on the hot path and optimized for speed. Use
 * efficient algorithms (e.g., nextInt(bound) instead of nextDouble() * bound).
 *
 * <p><b>Thread Safety:</b> All generators are stateless and thread-safe.
 */
package com.datagenerator.generators.primitive;
