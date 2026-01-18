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
 * instance to avoid contention and race conditions.
 *
 * <p>2. <b>Determinism:</b> For reproducibility, the same master seed must produce the same data
 * across multiple runs, even with parallel generation. We achieve this by:
 *
 * <ul>
 *   <li>Using a master seed from the job configuration
 *   <li>Assigning logical worker IDs (0, 1, 2, ...) to threads, NOT JVM thread IDs
 *   <li>Deriving deterministic per-thread seeds: {@code deriveSeed(masterSeed, workerID)}
 * </ul>
 *
 * <p><b>Why NOT JVM Thread IDs?</b>
 *
 * <p>JVM thread IDs ({@link Thread#threadId()}) are assigned sequentially as threads are created,
 * including system threads, GC threads, and application threads. They are NOT guaranteed to be the
 * same across JVM restarts:
 *
 * <pre>
 * Run 1: Worker threads get JVM IDs [15, 17, 19, 21] → derived seeds [X, Y, Z, W]
 * Run 2: Worker threads get JVM IDs [18, 20, 22, 24] → derived seeds [A, B, C, D] ❌ Different data!
 * </pre>
 *
 * <p><b>Solution: Logical Worker IDs</b>
 *
 * <p>We use an {@link AtomicInteger} counter to assign sequential worker IDs (0, 1, 2, ...) as
 * threads first request a Random instance. This ensures:
 *
 * <pre>
 * Run 1: Worker 0 → seed X, Worker 1 → seed Y, Worker 2 → seed Z
 * Run 2: Worker 0 → seed X, Worker 1 → seed Y, Worker 2 → seed Z ✅ Identical data!
 * </pre>
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * RandomProvider provider = new RandomProvider(12345L);
 * // Each thread calls:
 * Random random = provider.getRandom();
 * int value = random.nextInt(100); // Deterministic based on worker ID
 * </pre>
 *
 * @see SeedResolver
 */
@Slf4j
public class RandomProvider {
  private final long masterSeed;
  private final AtomicInteger workerIdCounter = new AtomicInteger(0);
  private final ThreadLocal<Random> threadLocalRandom;

  public RandomProvider(long masterSeed) {
    this.masterSeed = masterSeed;
    this.threadLocalRandom =
        ThreadLocal.withInitial(
            () -> {
              // Assign logical worker ID (0, 1, 2, ...) not JVM thread ID
              int workerId = workerIdCounter.getAndIncrement();
              long threadSeed = deriveSeed(masterSeed, workerId);
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
  private long deriveSeed(long masterSeed, int workerId) {
    long seed = masterSeed;
    seed ^= workerId; // Mix in worker ID
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
