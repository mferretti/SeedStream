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
 * File destination support for writing generated data to local files.
 *
 * <p>This package provides high-performance file writing using Java NIO with features like
 * compression, batching, and buffering.
 *
 * <p><b>Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.destinations.file.FileDestination} - File writer implementation
 *   <li>{@link com.datagenerator.destinations.file.FileDestinationConfig} - Configuration (path,
 *       compression, batching)
 * </ul>
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li><b>Java NIO:</b> Fast I/O using BufferedWriter and Files API
 *   <li><b>Compression:</b> Optional gzip compression (transparent to callers)
 *   <li><b>Batching:</b> Write multiple records in single flush (2-3x performance gain)
 *   <li><b>Buffering:</b> Configurable buffer size (default 8KB)
 *   <li><b>Append Mode:</b> Append to existing files or overwrite
 *   <li><b>Directory Creation:</b> Auto-creates parent directories
 * </ul>
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * type: file
 * conf:
 *   path: output/users.json     # Output file path
 *   compress: true               # Enable gzip compression (.json.gz)
 *   batch_size: 1000             # Records per batch write
 * </pre>
 *
 * <p><b>Performance Tips:</b>
 *
 * <ul>
 *   <li>Use batching (batch_size: 500-1000) for 2-3x performance improvement
 *   <li>Enable compression for large datasets (reduces disk I/O)
 *   <li>Use SSD storage for best write throughput
 *   <li>Increase buffer size for very high throughput scenarios
 * </ul>
 */
package com.datagenerator.destinations.file;
