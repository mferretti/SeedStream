package com.datagenerator.core.engine;

import com.datagenerator.core.seed.RandomProvider;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

/**
 * Multi-threaded data generation engine with deterministic seeding.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Parallel generation using worker threads
 *   <li>Deterministic output (same seed → same data)
 *   <li>Backpressure handling with bounded queue
 *   <li>Single writer thread for ordered writes
 *   <li>Auto-optimization for small jobs (single-threaded)
 *   <li>Progress logging
 * </ul>
 *
 * <p><b>Architecture:</b>
 *
 * <pre>
 * Worker Thread 0 (seed derived) → Generate → Serialize →\
 * Worker Thread 1 (seed derived) → Generate → Serialize → } → Queue → Writer Thread → Destination
 * Worker Thread N (seed derived) → Generate → Serialize →/
 * </pre>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * GenerationEngine engine = GenerationEngine.builder()
 *     .recordGenerator((random) -> generator.generate(random, objectType))
 *     .recordWriter(destination::write)
 *     .masterSeed(12345L)
 *     .workerThreads(8)
 *     .build();
 *
 * engine.generate(1000000); // Generate 1M records in parallel
 * </pre>
 *
 * <p><b>Thread Safety:</b> Thread-safe. Multiple workers can generate concurrently.
 */
@Slf4j
@Builder
public class GenerationEngine {

  /** Generator function for creating records. */
  private final RecordGenerator recordGenerator;

  /** Writer for records (typically destination::write). */
  private final RecordWriter recordWriter;

  /** Master seed for deterministic generation. */
  private final long masterSeed;

  /** Number of worker threads (default: # of CPU cores). */
  @Builder.Default private final int workerThreads = Runtime.getRuntime().availableProcessors();

  /** Batch size for progress logging (default: 10000). */
  @Builder.Default private final int logBatchSize = 10000;

  /** Queue capacity for backpressure (default: 1000). */
  @Builder.Default private final int queueCapacity = 1000;

  /** Threshold for auto-switching to single-threaded mode (default: 1000). */
  @Builder.Default private final int singleThreadedThreshold = 1000;

  /**
   * Generate specified number of records.
   *
   * <p>For small counts (< singleThreadedThreshold), uses single thread to avoid overhead. For
   * large counts, uses parallel workers.
   *
   * @param count Total number of records to generate
   * @throws InterruptedException if interrupted during generation
   */
  public void generate(long count) throws InterruptedException {
    if (count <= 0) {
      log.warn("Record count is {}, skipping generation", count);
      return;
    }

    // For small jobs, use single thread (avoid overhead)
    if (count < singleThreadedThreshold) {
      log.info("Small job ({} records), using single thread", count);
      generateSingleThreaded(count);
      return;
    }

    // For large jobs, use multi-threaded approach
    log.info("Starting parallel generation: {} records using {} workers", count, workerThreads);
    generateMultiThreaded(count);
  }

  /**
   * Single-threaded generation for small jobs.
   *
   * @param count Number of records to generate
   */
  private void generateSingleThreaded(long count) {
    RandomProvider randomProvider = new RandomProvider(masterSeed);
    Random random = randomProvider.getRandom();

    long startTime = System.currentTimeMillis();

    for (long i = 0; i < count; i++) {
      Map<String, Object> record = recordGenerator.generate(random);
      recordWriter.write(record);

      // Progress logging
      if ((i + 1) % logBatchSize == 0) {
        logProgress(i + 1, count, startTime);
      }
    }

    logProgress(count, count, startTime); // Final progress
    log.info("Single-threaded generation complete");
  }

  /**
   * Multi-threaded generation for large jobs.
   *
   * @param count Number of records to generate
   * @throws InterruptedException if interrupted
   */
  private void generateMultiThreaded(long count) throws InterruptedException {
    RandomProvider randomProvider = new RandomProvider(masterSeed);

    // Create bounded queue for backpressure
    BlockingQueue<Map<String, Object>> recordQueue = new ArrayBlockingQueue<>(queueCapacity);

    // Poison pill to signal end of generation
    @SuppressWarnings("unchecked")
    Map<String, Object> POISON_PILL = Map.of("__POISON_PILL__", true);

    // Atomic counter for generated records
    AtomicLong generated = new AtomicLong(0);

    long startTime = System.currentTimeMillis();

    // Start writer thread
    Thread writerThread =
        new Thread(
            () -> {
              try {
                while (true) {
                  Map<String, Object> record = recordQueue.take();
                  if (record == POISON_PILL) {
                    break;
                  }
                  recordWriter.write(record);
                }
                log.debug("Writer thread finished");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Writer thread interrupted", e);
              } catch (Exception e) {
                log.error("Writer thread failed", e);
                throw new RuntimeException("Writer thread failed", e);
              }
            },
            "writer-thread");
    writerThread.start();

    // Start worker threads
    ExecutorService workers = Executors.newFixedThreadPool(workerThreads);

    // Distribute work across workers
    long recordsPerWorker = count / workerThreads;
    long remainder = count % workerThreads;

    for (int workerId = 0; workerId < workerThreads; workerId++) {
      long workerCount = recordsPerWorker + (workerId < remainder ? 1 : 0);
      int finalWorkerId = workerId;

      workers.submit(
          () -> {
            try {
              generateWorkerRecords(
                  finalWorkerId,
                  workerCount,
                  randomProvider,
                  recordQueue,
                  generated,
                  count,
                  startTime);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              log.error("Worker {} interrupted", finalWorkerId, e);
            } catch (Exception e) {
              log.error("Worker {} failed", finalWorkerId, e);
              throw new RuntimeException("Worker " + finalWorkerId + " failed", e);
            }
          });
    }

    // Shutdown workers and wait for completion
    workers.shutdown();
    boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    if (!terminated) {
      log.warn("Worker threads did not terminate gracefully");
    }

    // Signal writer to stop
    recordQueue.put(POISON_PILL);
    writerThread.join();

    logProgress(count, count, startTime); // Final progress
    log.info(
        "Parallel generation complete: {} records generated by {} workers", count, workerThreads);
  }

  /**
   * Generate records for a single worker.
   *
   * @param workerId Worker ID (0-based)
   * @param count Number of records this worker should generate
   * @param randomProvider RandomProvider for getting thread-local Random
   * @param queue Queue for submitting generated records
   * @param generated Atomic counter for total generated records
   * @param totalCount Total record count (for progress logging)
   * @param startTime Start time for throughput calculation
   * @throws InterruptedException if interrupted
   */
  private void generateWorkerRecords(
      int workerId,
      long count,
      RandomProvider randomProvider,
      BlockingQueue<Map<String, Object>> queue,
      AtomicLong generated,
      long totalCount,
      long startTime)
      throws InterruptedException {

    // Get thread-local Random for this worker
    Random random = randomProvider.getRandom();

    long workerGenerated = 0;
    while (workerGenerated < count) {
      // Generate record
      Map<String, Object> record = recordGenerator.generate(random);

      // Submit to queue (blocks if queue is full - backpressure)
      queue.put(record);

      workerGenerated++;
      long totalGenerated = generated.incrementAndGet();

      // Progress logging
      if (totalGenerated % logBatchSize == 0) {
        logProgress(totalGenerated, totalCount, startTime);
      }
    }

    log.debug("Worker {} completed: generated {} records", workerId, workerGenerated);
  }

  /**
   * Log progress with throughput calculation.
   *
   * @param current Current count
   * @param total Total count
   * @param startTime Start time in milliseconds
   */
  private void logProgress(long current, long total, long startTime) {
    double progress = (current * 100.0) / total;
    long elapsed = System.currentTimeMillis() - startTime;
    double recordsPerSec = (elapsed > 0) ? current / (elapsed / 1000.0) : 0;

    log.info(
        "Progress: {} / {} ({:.1f}%) - {:.0f} records/sec",
        current, total, progress, recordsPerSec);
  }

  /**
   * Functional interface for generating records.
   *
   * <p>Typically implemented as: (random) -> generator.generate(random, dataType)
   */
  @FunctionalInterface
  public interface RecordGenerator {
    Map<String, Object> generate(Random random);
  }

  /**
   * Functional interface for writing records.
   *
   * <p>Typically implemented by destination::write
   */
  @FunctionalInterface
  public interface RecordWriter {
    void write(Map<String, Object> record);
  }
}
