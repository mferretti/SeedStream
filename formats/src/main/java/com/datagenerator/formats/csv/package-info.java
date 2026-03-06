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
 * CSV serialization support.
 *
 * <p>This package provides CSV serialization using Apache Commons CSV for RFC 4180-compliant output.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.formats.csv.CsvSerializer} - Serializes records to CSV format with
 *       headers
 * </ul>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>RFC 4180 compliant (official CSV standard)
 *   <li>Proper escaping of commas, quotes, and newlines
 *   <li>Header row with field names
 *   <li>Consistent field ordering across records
 * </ul>
 *
 * <p><b>Example Output:</b>
 *
 * <pre>
 * user_id,name,age
 * a1b2c3,"John Doe",30
 * d4e5f6,"Jane Smith",28
 * g7h8i9,"Bob Jones",35
 * </pre>
 */
package com.datagenerator.formats.csv;
