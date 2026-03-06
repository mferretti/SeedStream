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
 * Multi-threaded data generation engine with parallel workers and deterministic seeding.
 *
 * <p>This package provides the core generation engine that orchestrates parallel data production,
 * coordinating multiple worker threads while maintaining deterministic output and efficient
 * resource utilization.
 *
 * <p><b>Architecture:</b>
 *
 * <pre>
 * Worker Thread 0 (seed-derived) → Generate → Serialize →\
 * Worker Thread 1 (seed-derived) → Generate → Serialize → } → Queue → Writer Thread → Destination
 * Worker Thread N (seed-derived) → Generate → Serialize →/
 * </pre>
 *
 * <p><b>Key Features:</b>
 *
 * <ul>
 *   <li><b>Parallel Generation:</b> Multiple worker threads generate records concurrently
 *   <li><b>Deterministic Output:</b> Same seed produces same data regardless of thread count
 *   <li><b>Backpressure Handling:</b> Bounded queue prevents memory overflow
 *   <li><b>Single Writer:</b> Dedicated thread for ordered writes to destination
 *   <li><b>Auto-Optimization:</b> Single-threaded mode for small jobs (&lt;1000 records)
 *   <li><b>Progress Tracking:</b> Periodic logging of generation progress
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * GenerationEngine engine = GenerationEngine.builder()
 *     .recordGenerator((random) -&gt; generator.generate(random, objectType, context))
 *     .recordWriter(destination::write)
 *     .masterSeed(12345L)
 *     .workerThreads(8)
 *     .queueCapacity(10000)
 *     .build();
 *
 * // Generate 1 million records in parallel
 * engine.generate(1_000_000);
 * </pre>
 *
 * <p><b>Performance Optimization:</b>
 *
 * <ul>
 *   <li>Worker count defaults to {@code Runtime.availableProcessors()}
 *   <li>Queue capacity balances memory usage vs throughput (default: 10,000)
 *   <li>Single-threaded mode avoids overhead for small datasets
 *   <li>Thread-local Random instances prevent contention
 * </ul>
 *
 * <p><b>Thread Safety:</b> The engine is thread-safe. Worker threads operate independently with
 * thread-local state, and a single writer thread handles destination writes.
 */
package com.datagenerator.core.engine;
