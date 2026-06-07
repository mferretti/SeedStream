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

package com.datagenerator.generators.semantic;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Thread-local cache for Datafaker instances to avoid repeated initialization.
 *
 * <p>Each thread maintains a cache of Faker instances keyed by Locale. This dramatically reduces
 * overhead when generating many values with same locale.
 *
 * <p><b>Performance Impact:</b>
 *
 * <ul>
 *   <li>Without cache: NEW Faker for every value (800K instantiations per 100K records)
 *   <li>With cache: ONE Faker per locale per thread (typically 1-2 instances total)
 * </ul>
 *
 * <p><b>Thread Safety:</b> ThreadLocal ensures each thread has its own cache with no contention.
 *
 * <p><b>Determinism:</b> Each cached Faker is seeded with thread-local Random, maintaining
 * reproducible output.
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // In DatafakerGenerator
 * Faker faker = FakerCache.getOrCreate(locale, random);  // Reuses if exists
 * String name = faker.name().fullName();
 * </pre>
 */
@Slf4j
public class FakerCache {

  // Thread-local cache: Locale -> Faker instance
  private static final ThreadLocal<Map<Locale, Faker>> CACHE =
      ThreadLocal.withInitial(ConcurrentHashMap::new);

  // Thread-local Random from RandomProvider (passed via context)
  private static final ThreadLocal<Random> THREAD_RANDOM = new ThreadLocal<>();

  /**
   * Get or create a Faker instance for the given locale.
   *
   * <p><b>Thread Safety &amp; Reproducibility:</b>
   *
   * <ul>
   *   <li>Each thread has its own cache (ThreadLocal)
   *   <li>Each thread receives a deterministic Random from RandomProvider (seed = master +
   *       workerID)
   *   <li>The Random parameter MUST be the same thread-local instance for all calls in a thread
   *   <li>Faker stores a REFERENCE to the Random, so as Random state progresses, Faker uses it
   * </ul>
   *
   * <p><b>Multi-threading Example:</b>
   *
   * <pre>
   * Thread 1 (workerID=0): Random(seed_0) → Faker(locale, Random(seed_0))
   * Thread 2 (workerID=1): Random(seed_1) → Faker(locale, Random(seed_1))
   * Each thread generates different but deterministic data based on its worker seed.
   * </pre>
   *
   * @param locale Target locale for data generation
   * @param random Thread-local Random instance (MUST be same instance for all calls in thread)
   * @return Cached Faker instance for this locale
   */
  public static Faker getOrCreate(Locale locale, Random random) {
    // Validate that Random is consistent within thread
    Random storedRandom = THREAD_RANDOM.get();
    if (storedRandom == null) {
      THREAD_RANDOM.set(random);
      log.debug("Initialized FakerCache for thread: {}", Thread.currentThread().getName());
    } else if (storedRandom != random) {
      // This should NEVER happen with RandomProvider (thread-local Random)
      // But catch it to prevent subtle bugs if API is misused
      log.warn(
          "FakerCache detected different Random instance in thread {}. "
              + "This breaks determinism! Expected same thread-local Random from RandomProvider.",
          Thread.currentThread().getName());
      // Use the first Random to maintain consistency
    }

    Map<Locale, Faker> cache = CACHE.get();

    // Get or create Faker for this locale
    return cache.computeIfAbsent(
        locale,
        loc -> {
          log.debug(
              "Creating new Faker for locale: {} in thread: {}",
              loc,
              Thread.currentThread().getName());
          return new Faker(loc, THREAD_RANDOM.get());
        });
  }

  /**
   * Clear the cache for the current thread.
   *
   * <p>Should be called when reusing threads (e.g., servlet containers, long-running thread pools)
   * to prevent memory leaks. For short-lived worker threads that terminate after job completion,
   * cleanup is automatic (ThreadLocal cleared on thread termination).
   *
   * <p><b>When to call:</b>
   *
   * <ul>
   *   <li>Unit tests: @AfterEach cleanup
   *   <li>REST API: End of request in thread pool
   *   <li>Long-running applications: Periodic cleanup
   * </ul>
   *
   * <p><b>Not needed:</b>
   *
   * <ul>
   *   <li>CLI execution: Worker threads terminate after job
   *   <li>Short-lived processes: JVM exit handles cleanup
   * </ul>
   *
   * <p>After cleanup, next access will create fresh instances.
   */
  public static void clear() {
    CACHE.remove();
    THREAD_RANDOM.remove();
    log.debug("Cleared FakerCache for thread: {}", Thread.currentThread().getName());
  }

  /**
   * Get cache statistics for current thread (for debugging/profiling).
   *
   * @return Number of Faker instances cached in current thread
   */
  public static int getCacheSize() {
    return CACHE.get().size();
  }
}
