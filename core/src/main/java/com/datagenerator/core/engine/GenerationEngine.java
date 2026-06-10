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

package com.datagenerator.core.engine;

import com.datagenerator.core.seed.RandomProvider;
import com.datagenerator.core.util.LogUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
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
 * <p><b>Architecture:</b> workers generate records and batch them into chunks; a single writer
 * thread drains chunks and performs serialization + ordered I/O on the destination. Serialization
 * lives on the writer side because destinations (single output stream, Avro OCF container, Kafka
 * record counter) are ordered/stateful, not because generation is serial.
 *
 * <pre>
 * Worker Thread 0 (seed derived) → Generate → chunk →\
 * Worker Thread 1 (seed derived) → Generate → chunk → } → Queue → Writer Thread → Serialize → Destination
 * Worker Thread N (seed derived) → Generate → chunk →/
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

  /**
   * Optional: serializes a record to bytes. When set (together with {@link #serializedWriter}),
   * serialization runs on the worker threads instead of the single writer thread, parallelizing the
   * heaviest CPU stage. Leave unset to flow the raw {@code Map} through and let the destination
   * serialize. Only safe for formats whose records are independently encodable (e.g. NDJSON, Kafka
   * payloads) — not Avro OCF, which the writer must serialize into its ordered container.
   */
  private final RecordSerializer recordSerializer;

  /** Optional: writes pre-serialized bytes; required when {@link #recordSerializer} is set. */
  private final SerializedWriter serializedWriter;

  /** Master seed for deterministic generation. */
  private final long masterSeed;

  /** Number of worker threads (default: # of CPU cores). */
  @Builder.Default private final int workerThreads = Runtime.getRuntime().availableProcessors();

  /** Batch size for progress logging (default: 10000). */
  @SuppressWarnings("java:S1170")
  @Builder.Default
  private final int logBatchSize = 10000;

  /** Queue capacity for backpressure, expressed in records (default: 1000). */
  @SuppressWarnings("java:S1170")
  @Builder.Default
  private final int queueCapacity = 1000;

  /**
   * Number of records batched into a single queue hand-off (default: 256).
   *
   * <p>Workers accumulate records into a chunk and enqueue the whole chunk at once, amortizing the
   * per-record {@code put}/{@code take} lock + signal cost on the hot path by ~chunkSize.
   */
  @SuppressWarnings("java:S1170")
  @Builder.Default
  private final int chunkSize = 256;

  /** Threshold for auto-switching to single-threaded mode (default: 1000). */
  @SuppressWarnings("java:S1170")
  @Builder.Default
  private final int singleThreadedThreshold = 1000;

  /**
   * Optional cleanup to run on each worker thread after it finishes generating.
   *
   * <p>Use this to clear thread-local state (e.g., {@code FakerCache::clear}) when worker threads
   * may be reused across multiple jobs. For short-lived CLI processes this is not strictly
   * necessary, but it is good practice in long-running or embedded use cases.
   *
   * <p>Defaults to a no-op.
   */
  @SuppressWarnings("java:S1170")
  @Builder.Default
  private final Runnable workerCleanup = () -> {};

  /**
   * Generate specified number of records.
   *
   * <p>For small counts (&lt; singleThreadedThreshold), uses single thread to avoid overhead. For
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

    // Choose the payload pipeline. When a record serializer is configured, serialization is folded
    // into the worker (producer) side so it runs in parallel; the single writer thread then only
    // performs ordered I/O. Otherwise the raw Map flows through and the destination serializes.
    if (recordSerializer != null && serializedWriter != null) {
      runPipeline(
          count,
          random -> recordSerializer.serialize(recordGenerator.generate(random)),
          serializedWriter::write);
    } else {
      runPipeline(count, recordGenerator::generate, recordWriter::write);
    }
  }

  /**
   * Dispatch to single- or multi-threaded execution based on job size.
   *
   * @param <P> payload type carried worker → writer (raw {@code Map} or serialized {@code byte[]})
   * @param count number of records to generate
   * @param produce builds one payload from a thread-local {@link Random} (runs on workers)
   * @param consume writes one payload (runs on the single writer thread)
   */
  private <P> void runPipeline(long count, Function<Random, P> produce, Consumer<P> consume)
      throws InterruptedException {
    if (count < singleThreadedThreshold) {
      log.info("Small job ({} records), using single thread", count);
      runSingleThreaded(count, produce, consume);
    } else {
      log.info("Starting parallel generation: {} records using {} workers", count, workerThreads);
      runMultiThreaded(count, produce, consume);
    }
  }

  /** Single-threaded generation for small jobs. */
  private <P> void runSingleThreaded(long count, Function<Random, P> produce, Consumer<P> consume) {
    RandomProvider randomProvider = new RandomProvider(masterSeed);
    Random random = randomProvider.getRandom();

    long startTime = System.currentTimeMillis();

    for (long i = 0; i < count; i++) {
      consume.accept(produce.apply(random));

      // Progress logging
      if ((i + 1) % logBatchSize == 0) {
        logProgress(i + 1, count, startTime);
      }
    }

    logProgress(count, count, startTime); // Final progress
    workerCleanup.run();
    log.info("Single-threaded generation complete");
  }

  /**
   * Multi-threaded generation for large jobs.
   *
   * @param count Number of records to generate
   * @throws InterruptedException if interrupted
   */
  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
      justification =
          "Worker Future is intentionally not stored; exceptions are logged in the lambda "
              + "and the writer thread propagates failures via the queue poison-pill shutdown")
  @SuppressWarnings({"PMD.AvoidCatchingGenericException", "java:S3776"})
  private <P> void runMultiThreaded(long count, Function<Random, P> produce, Consumer<P> consume)
      throws InterruptedException {
    RandomProvider randomProvider = new RandomProvider(masterSeed);

    // Bounded queue of record chunks for backpressure. queueCapacity is expressed in records, but
    // the queue carries chunks, so derive a chunk-granular capacity that buffers a similar number
    // of records in flight.
    int chunkQueueCapacity = Math.max(1, queueCapacity / chunkSize);
    BlockingQueue<List<P>> recordQueue = new ArrayBlockingQueue<>(chunkQueueCapacity);

    // Poison pill (identity-compared) to signal end of generation
    final List<P> poisonPill = new ArrayList<>(0);

    // Atomic counter for generated records
    AtomicLong generated = new AtomicLong(0);

    long startTime = System.currentTimeMillis();

    // Start writer thread
    Thread writerThread =
        new Thread(
            () -> {
              try {
                while (true) {
                  List<P> chunk = recordQueue.take();
                  if (chunk == poisonPill) {
                    break;
                  }
                  for (P item : chunk) {
                    consume.accept(item);
                  }
                }
                log.debug("Writer thread finished");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Writer thread interrupted", e);
              } catch (Exception e) {
                log.error("Writer thread failed", e);
                throw new IllegalStateException("Writer thread failed", e);
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
                  produce,
                  recordQueue,
                  generated,
                  count,
                  startTime);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              log.error("Worker {} interrupted", finalWorkerId, e);
            } catch (Exception e) {
              log.error("Worker {} failed", finalWorkerId, e);
              throw new IllegalStateException("Worker " + finalWorkerId + " failed", e);
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
    recordQueue.put(poisonPill);
    writerThread.join();

    logProgress(count, count, startTime); // Final progress
    log.info(
        "Parallel generation complete: {} records generated by {} workers", count, workerThreads);
  }

  /**
   * Generate records for a single worker.
   *
   * @param <P> payload type carried worker → writer
   * @param workerId Worker ID (0-based)
   * @param count Number of records this worker should generate
   * @param randomProvider RandomProvider for getting thread-local Random
   * @param produce builds (and optionally serializes) one payload from a Random
   * @param queue Queue for submitting generated record chunks
   * @param generated Atomic counter for total generated records
   * @param totalCount Total record count (for progress logging)
   * @param startTime Start time for throughput calculation
   * @throws InterruptedException if interrupted
   */
  private <P> void generateWorkerRecords(
      int workerId,
      long count,
      RandomProvider randomProvider,
      Function<Random, P> produce,
      BlockingQueue<List<P>> queue,
      AtomicLong generated,
      long totalCount,
      long startTime)
      throws InterruptedException {

    // Get thread-local Random for this worker
    Random random = randomProvider.getRandom();

    long workerGenerated = 0;
    List<P> chunk = new ArrayList<>(chunkSize);
    while (workerGenerated < count) {
      // Generate (and, on the serialized pipeline, serialize) one payload
      P item = produce.apply(random);

      // TRACE log individual record generation (sampled)
      if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
        log.trace("Worker {} generated record {}", workerId, workerGenerated + 1);
      }

      // Accumulate into a chunk; hand off the whole chunk to amortize queue lock/signal overhead
      chunk.add(item);
      if (chunk.size() >= chunkSize) {
        queue.put(chunk); // blocks if queue is full - backpressure
        chunk = new ArrayList<>(chunkSize);
      }

      workerGenerated++;
      long totalGenerated = generated.incrementAndGet();

      // Progress logging
      if (totalGenerated % logBatchSize == 0) {
        logProgress(totalGenerated, totalCount, startTime);
      }
    }

    // Flush any trailing partial chunk
    if (!chunk.isEmpty()) {
      queue.put(chunk);
    }

    log.debug("Worker {} completed: generated {} records", workerId, workerGenerated);
    workerCleanup.run();
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
        "Progress: {} / {} ({}%) - {} records/sec",
        current, total, String.format("%.1f", progress), String.format("%.0f", recordsPerSec));
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
    void write(Map<String, Object> data);
  }

  /**
   * Functional interface for serializing a record to bytes on the worker thread.
   *
   * <p>Typically implemented as {@code serializer::serializeToBytes}. Supplying one to the engine
   * moves serialization off the single writer thread and onto the parallel workers.
   */
  @FunctionalInterface
  public interface RecordSerializer {
    byte[] serialize(Map<String, Object> data);
  }

  /**
   * Functional interface for writing pre-serialized record bytes.
   *
   * <p>Typically implemented by {@code destination::writeSerialized}. Runs on the single writer
   * thread, preserving ordered/stateful destination semantics.
   */
  @FunctionalInterface
  public interface SerializedWriter {
    void write(byte[] payload);
  }
}
