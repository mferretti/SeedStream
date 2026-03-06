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
 * Output format serializers for converting generated records to various formats.
 *
 * <p>This package provides serializers that convert Map&lt;String, Object&gt; records (generated
 * data) into formatted strings for writing to destinations. Serializers are on the hot path and
 * must be optimized for performance.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.formats.FormatSerializer} - Base interface for all serializers
 *   <li>{@link com.datagenerator.formats.json.JsonSerializer} - JSON serialization
 *       (newline-delimited)
 *   <li>{@link com.datagenerator.formats.csv.CsvSerializer} - CSV serialization with headers
 * </ul>
 *
 * <p><b>Supported Formats:</b>
 *
 * <ul>
 *   <li><b>JSON:</b> Newline-delimited JSON (JSONL) - one record per line
 *   <li><b>CSV:</b> Comma-separated values with header row
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * Map&lt;String, Object&gt; record = Map.of(
 *     "name", "John Doe",
 *     "age", 30,
 *     "email", "john@example.com"
 * );
 *
 * FormatSerializer jsonSerializer = new JsonSerializer();
 * String json = jsonSerializer.serialize(record);
 * // Output: {"name":"John Doe","age":30,"email":"john@example.com"}
 *
 * FormatSerializer csvSerializer = new CsvSerializer();
 * csvSerializer.initialize(record.keySet().stream().toList());
 * String csv = csvSerializer.serialize(record);
 * // Output: John Doe,30,john@example.com
 * </pre>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Serializers are called for every record - optimize for speed
 *   <li>JSON uses Jackson streaming API for low memory footprint
 *   <li>CSV uses Apache Commons CSV for RFC 4180 compliance
 *   <li>All serializers should be stateless for thread safety
 * </ul>
 *
 * <p><b>Thread Safety:</b> All serializers must be thread-safe for concurrent use by multiple
 * worker threads.
 */
package com.datagenerator.formats;
