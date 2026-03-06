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
 * Semantic type generators using Datafaker for realistic, locale-aware test data.
 *
 * <p>This package provides generators for realistic data like names, addresses, phone numbers,
 * emails, and other domain-specific values. Datafaker generates locale-appropriate data based on the
 * geolocation setting in the data structure definition.
 *
 * <p><b>Generator:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.generators.semantic.DatafakerGenerator} - Delegates to Datafaker
 *       for 30+ semantic types
 * </ul>
 *
 * <p><b>Supported Semantic Types:</b>
 *
 * <ul>
 *   <li><b>Person:</b> name, first_name, last_name, full_name, username, title, occupation
 *   <li><b>Address:</b> address, street_name, street_number, city, state, postal_code, country
 *   <li><b>Contact:</b> email, phone_number
 *   <li><b>Finance:</b> company, credit_card, iban, currency, price
 *   <li><b>Internet:</b> domain, url, ipv4, ipv6, mac_address
 *   <li><b>Codes:</b> isbn, uuid
 * </ul>
 *
 * <p><b>Locale Support:</b> The generator uses {@link
 * com.datagenerator.generators.LocaleMapper} to convert geolocation strings (e.g., "italy", "brazil",
 * "japan") into Java Locale objects for Datafaker.
 *
 * <p><b>Determinism:</b> Datafaker is seeded with the provided Random instance to ensure
 * reproducibility (same seed → same names, addresses, etc.).
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * # Data structure with geolocation
 * name: user
 * geolocation: italy
 * data:
 *   name:
 *     datatype: name      # Generates Italian names (e.g., "Marco Rossi")
 *   email:
 *     datatype: email     # Generates emails with Italian-sounding names
 *   city:
 *     datatype: city      # Generates Italian cities (e.g., "Roma", "Milano")
 * </pre>
 *
 * <p><b>Thread Safety:</b> Datafaker instances are thread-local for thread-safe access.
 */
package com.datagenerator.generators.semantic;
