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
 * Deterministic seeding and random number generation for reproducible test data.
 *
 * <p>This package provides the seeding infrastructure that ensures the same seed value always
 * produces the same generated data, even across multiple runs and parallel threads.
 *
 * <p><b>Core Components:</b>
 *
 * <ul>
 *   <li>{@link com.datagenerator.core.seed.SeedConfig} - Configuration for seed sources (embedded,
 *       file, environment, remote)
 *   <li>{@link com.datagenerator.core.seed.SeedResolver} - Resolves seed from various sources
 *   <li>{@link com.datagenerator.core.seed.RandomProvider} - Provides thread-local Random instances
 *       with deterministic seeding
 * </ul>
 *
 * <p><b>Seed Sources:</b>
 *
 * <pre>
 * # Embedded seed (directly in YAML)
 * seed:
 *   type: embedded
 *   value: 12345
 *
 * # File-based seed (read from file)
 * seed:
 *   type: file
 *   path: /secure/seed.txt
 *
 * # Environment variable seed
 * seed:
 *   type: env
 *   name: DATA_GEN_SEED
 *
 * # Remote API seed (with authentication)
 * seed:
 *   type: remote
 *   url: https://api.example.com/seed
 *   auth:
 *     type: bearer
 *     token: ${API_TOKEN}
 * </pre>
 *
 * <p><b>Deterministic Generation:</b> The {@link com.datagenerator.core.seed.RandomProvider}
 * ensures reproducibility in parallel generation by:
 *
 * <ol>
 *   <li>Using a master seed from configuration
 *   <li>Assigning logical worker IDs (0, 1, 2, ...) to threads
 *   <li>Deriving deterministic per-thread seeds using {@code masterSeed XOR (workerID * prime)}
 *   <li>Providing thread-local Random instances to avoid contention
 * </ol>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * // Resolve seed from configuration
 * SeedResolver resolver = new SeedResolver();
 * long seed = resolver.resolve(seedConfig);
 *
 * // Create provider with master seed
 * RandomProvider provider = new RandomProvider(seed);
 *
 * // Get thread-local Random (automatically seeded)
 * Random random = provider.getRandom();
 *
 * // Use for generation
 * int value = random.nextInt(100);
 * </pre>
 *
 * <p><b>Thread Safety:</b> RandomProvider uses ThreadLocal for thread-safe Random access without
 * synchronization overhead.
 *
 * @see com.datagenerator.core.engine.GenerationEngine
 * @see java.util.Random
 */
package com.datagenerator.core.seed;
