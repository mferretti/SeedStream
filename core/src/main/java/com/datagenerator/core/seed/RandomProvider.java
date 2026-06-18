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

package com.datagenerator.core.seed;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides thread-local Random instances with deterministic seeding for reproducible data
 * generation across multiple runs.
 *
 * <p><b>Design Rationale:</b>
 *
 * <p>1. <b>Thread Safety:</b> {@link Random} is not thread-safe. Each worker thread needs its own
 * instance to avoid contention and race conditions — {@link #getRandom()} provides a reusable
 * thread-local instance for that purpose.
 *
 * <p>2. <b>Determinism (per-record seeding):</b> reproducibility is guaranteed by seeding each
 * record from its <b>global record index</b> via {@link #deriveRecordSeed(long)}, not from the
 * worker that happens to generate it. The engine reseeds the thread-local {@link Random} with
 * {@code deriveRecordSeed(globalIndex)} before generating record {@code globalIndex}. Because the
 * seed of record {@code i} depends only on the master seed and {@code i}:
 *
 * <ul>
 *   <li>the same master seed produces the same value at every index, across runs;
 *   <li>the result is independent of thread count and core count — partitioning the work
 *       differently cannot change any record's value (the property a per-worker sequential RNG
 *       could not give).
 * </ul>
 *
 * <p><b>Why index-based, not JVM thread IDs?</b> JVM thread IDs ({@link Thread#threadId()}) vary
 * across runs (system/GC threads are created in between), so anything derived from them is not
 * reproducible. The logical worker IDs assigned by {@link #getRandom()} (via an {@link
 * AtomicInteger}, not JVM IDs) keep the thread-local instances stable, but the per-record index
 * seed is what actually pins the data — and it removes the dependency on partitioning entirely.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * RandomProvider provider = new RandomProvider(12345L);
 * Random random = provider.getRandom();          // reusable per-thread instance
 * random.setSeed(provider.deriveRecordSeed(i));  // seed record i by its global index
 * int value = random.nextInt(100);               // deterministic for index i, any thread count
 * </pre>
 *
 * @see SeedResolver
 */
@Slf4j
public class RandomProvider {
  private final long masterSeed;
  private final AtomicInteger workerIdCounter = new AtomicInteger(0);
  private final ThreadLocal<Random> threadLocalRandom;

  public RandomProvider(long seed) {
    this.masterSeed = seed;
    this.threadLocalRandom =
        ThreadLocal.withInitial(
            () -> {
              // Assign logical worker ID (0, 1, 2, ...) not JVM thread ID
              int workerId = workerIdCounter.getAndIncrement();
              long threadSeed = deriveSeed(seed, workerId);
              log.debug(
                  "Creating Random for worker {} (thread {}) with seed {}",
                  workerId,
                  Thread.currentThread().getName(),
                  threadSeed);
              return new Random(threadSeed);
            });
  }

  /**
   * Get the Random instance for the current thread. First call assigns a logical worker ID and
   * creates a deterministic Random instance.
   *
   * @return thread-local Random instance
   */
  public Random getRandom() {
    return threadLocalRandom.get();
  }

  /**
   * Get the master seed used to initialize this provider.
   *
   * @return the master seed
   */
  public long getMasterSeed() {
    return masterSeed;
  }

  /**
   * Derive a deterministic seed for a worker from the master seed. Uses a mixing function to ensure
   * different workers get different but reproducible seeds.
   *
   * <p><b>Algorithm:</b> Simple hash-based mixing to spread bits:
   *
   * <pre>
   * seed = masterSeed
   * seed ^= workerId              // Mix in worker ID
   * seed ^= (seed << 21)          // Bit mixing for avalanche effect
   * seed ^= (seed >>> 35)         // Spread high bits to low bits
   * seed ^= (seed << 4)           // Final mixing
   * </pre>
   *
   * <p>This ensures that small changes in workerId produce large changes in the derived seed,
   * giving each worker a distinct random sequence.
   *
   * @param masterSeed the master seed from job configuration
   * @param workerId logical worker ID (0, 1, 2, ...)
   * @return derived seed for the worker
   */
  private long deriveSeed(long base, int workerId) {
    long seed = base;
    seed ^= workerId; // Mix in worker ID
    seed ^= (seed << 21); // Bit avalanche
    seed ^= (seed >>> 35); // Spread bits
    seed ^= (seed << 4); // Final mixing
    return seed;
  }

  /**
   * Derive a deterministic seed for a single record from its <b>global</b> index (0, 1, 2, ...),
   * independent of which worker thread generates it.
   *
   * <p>This is the key to thread-count- and machine-invariant output: record {@code i} is seeded
   * the same way no matter how the work is partitioned, so the generated value for index {@code i}
   * is identical across any thread count or core count. Same avalanche mixing as {@link
   * #deriveSeed(long, int)} but keyed on a {@code long} record index instead of a worker ID.
   *
   * @param index global record index (0-based)
   * @return derived seed for that record
   */
  public long deriveRecordSeed(long index) {
    long seed = masterSeed;
    seed ^= index; // Mix in record index
    seed ^= (seed << 21); // Bit avalanche
    seed ^= (seed >>> 35); // Spread bits
    seed ^= (seed << 4); // Final mixing
    return seed;
  }

  /**
   * Clean up thread-local resources. Should be called when shutting down thread pools to prevent
   * memory leaks.
   */
  public void cleanup() {
    threadLocalRandom.remove();
  }
}
