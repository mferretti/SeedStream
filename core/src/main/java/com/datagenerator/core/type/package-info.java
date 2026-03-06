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
 * Type system for data generation supporting primitives, composites, and semantic types.
 *
 * <p>The type system is the foundation of the data generator, defining all supported data types and
 * their constraints. Types are parsed from YAML configuration strings and used by generators to
 * produce values.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.core.type.DataType} - Base interface for all types
 *   <li>{@link com.datagenerator.core.type.PrimitiveType} - Simple types (char, int, decimal, date,
 *       etc.)
 *   <li>{@link com.datagenerator.core.type.EnumType} - Enumeration with predefined values
 *   <li>{@link com.datagenerator.core.type.ObjectType} - Nested structure references
 *   <li>{@link com.datagenerator.core.type.ArrayType} - Variable-length collections
 *   <li>{@link com.datagenerator.core.type.ReferenceType} - Foreign key references
 *   <li>{@link com.datagenerator.core.type.TypeParser} - Parses type strings from YAML
 * </ul>
 *
 * <p><b>Type Syntax Examples:</b>
 *
 * <pre>
 * char[5..20]                        // String with length 5-20
 * int[1..100]                        // Integer from 1 to 100
 * decimal[0.0..999.99]               // Decimal with range
 * date[2020-01-01..2025-12-31]       // Date range
 * timestamp[now-30d..now]            // Timestamp range (relative dates)
 * boolean                            // Boolean (no range)
 * enum[ACTIVE,INACTIVE,PENDING]      // Enumeration
 * object[address]                    // Nested structure reference
 * array[int[1..100], 5..10]          // Array of 5-10 integers
 * ref[user.user_id]                  // Foreign key reference
 * name                               // Semantic type (Datafaker)
 * email                              // Semantic type (Datafaker)
 * uuid                               // UUID generator
 * </pre>
 *
 * <p><b>Semantic Types:</b> The {@link com.datagenerator.core.type.PrimitiveType.Kind} enum
 * includes 30+ semantic types powered by Datafaker for generating realistic, locale-aware data such
 * as names, addresses, emails, phone numbers, companies, URLs, etc.
 *
 * <p><b>Type Parsing:</b> The {@link com.datagenerator.core.type.TypeParser} converts YAML datatype
 * strings into {@link com.datagenerator.core.type.DataType} instances. It uses regex patterns to
 * match type syntax and extract constraints (ranges, enum values, structure names).
 *
 * <p><b>Thread Safety:</b> All DataType implementations are immutable value objects (Lombok @Value)
 * and thread-safe.
 */
package com.datagenerator.core.type;
