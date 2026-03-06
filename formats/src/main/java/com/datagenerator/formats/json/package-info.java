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
 * JSON serialization support.
 *
 * <p>This package provides JSON serialization using Jackson for high-performance, streaming JSON
 * output.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.formats.json.JsonSerializer} - Serializes records to
 *       newline-delimited JSON
 * </ul>
 *
 * <p><b>Format:</b> Newline-delimited JSON (JSONL) - one JSON object per line, no root array. This
 * format is streaming-friendly and widely supported by data processing tools.
 *
 * <p><b>Example Output:</b>
 *
 * <pre>
 * {"user_id":"a1b2c3","name":"John Doe","age":30}
 * {"user_id":"d4e5f6","name":"Jane Smith","age":28}
 * {"user_id":"g7h8i9","name":"Bob Jones","age":35}
 * </pre>
 */
package com.datagenerator.formats.json;
