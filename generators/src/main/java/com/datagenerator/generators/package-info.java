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
 * Data generators for all supported types (primitives, composites, and semantic types).
 *
 * <p>This package provides the generator infrastructure that produces values for each {@link
 * com.datagenerator.core.type.DataType}. Generators use {@link java.util.Random} for deterministic
 * generation and are stateless for thread safety.
 *
 * <p><b>Generator Categories:</b>
 *
 * <ul>
 *   <li><b>Primitive Generators</b> (com.datagenerator.generators.primitive) - Simple types: char,
 *       int, decimal, boolean, date, timestamp
 *   <li><b>Composite Generators</b> (com.datagenerator.generators.composite) - Complex types:
 *       arrays, nested objects
 *   <li><b>Semantic Generators</b> (com.datagenerator.generators.semantic) - Realistic data using
 *       Datafaker (names, addresses, emails, etc.)
 * </ul>
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.generators.DataGenerator} - Base interface for all generators
 *   <li>{@link com.datagenerator.generators.DataGeneratorFactory} - Factory for creating generators
 *       based on type
 *   <li>{@link com.datagenerator.generators.GeneratorContext} - Context object for recursive
 *       generation (nested objects, arrays)
 *   <li>{@link com.datagenerator.generators.LocaleMapper} - Maps geolocation strings to Java
 *       Locales for Datafaker
 * </ul>
 *
 * <p><b>Generator Selection:</b> The factory iterates registered generators to find the first one
 * that can handle the given DataType:
 *
 * <pre>
 * DataType type = new PrimitiveType(PrimitiveType.Kind.INT, "1", "100");
 * DataGenerator generator = DataGeneratorFactory.create(type);
 * Object value = generator.generate(random, type, context);
 * </pre>
 *
 * <p><b>Supported Types:</b>
 *
 * <ul>
 *   <li><b>Primitives:</b> char, int, decimal, boolean, date, timestamp
 *   <li><b>Semantic:</b> name, first_name, last_name, full_name, username, title, occupation,
 *       address, street_name, street_number, city, state, postal_code, country, email,
 *       phone_number, company, credit_card, iban, currency, price, domain, url, ipv4, ipv6,
 *       mac_address, isbn, uuid
 *   <li><b>Enums:</b> Predefined value lists
 *   <li><b>Arrays:</b> Variable-length collections with inner type
 *   <li><b>Objects:</b> Nested structure references
 *   <li><b>References:</b> Foreign key references to other records
 * </ul>
 *
 * <p><b>Determinism:</b> All generators use the provided {@link java.util.Random} instance for
 * value generation, ensuring the same Random state produces the same values. Never use {@link
 * Math#random()} or create new Random instances internally.
 *
 * <p><b>Thread Safety:</b> Generators are stateless and thread-safe. Random instances are provided
 * per-call and are thread-local.
 *
 * <p><b>Locale Support:</b> Semantic generators respect the geolocation setting from data
 * structures to produce locale-appropriate data (e.g., Italian names for geolocation="italy").
 *
 * @see com.datagenerator.core.type.DataType
 * @see com.datagenerator.core.seed.RandomProvider
 */
package com.datagenerator.generators;
