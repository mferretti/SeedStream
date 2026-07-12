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
import java.util.concurrent.atomic.AtomicReference;
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
 *   <li>Deterministic output: same seed → byte-for-byte identical output, <b>regardless of thread
 *       count</b> (and therefore across machines with different core counts)
 *   <li>Backpressure handling with bounded per-worker queues
 *   <li>Single writer thread that merges workers in global record order
 *   <li>Auto-optimization for small jobs (single-threaded)
 *   <li>Progress logging
 * </ul>
 *
 * <p><b>Determinism model:</b> records are partitioned into fixed-size chunks of <i>global</i>
 * indices; chunk {@code c} (records {@code [c*chunkSize, (c+1)*chunkSize)}) is owned by worker
 * {@code c % activeWorkers}. Each record is seeded from its global index ({@link
 * RandomProvider#deriveRecordSeed(long)}), so the value at index {@code i} is independent of which
 * worker produced it or how many workers there are. The writer then merges the per-worker queues in
 * ascending chunk order, so the byte output is identical for any thread count.
 *
 * <p><b>Architecture:</b> each worker generates its chunks in order into its own bounded queue; a
 * single writer thread pulls chunk {@code c} from queue {@code c % activeWorkers} and serializes +
 * writes it. Serialization lives on the writer side because destinations (single output stream,
 * Avro OCF container, Kafka record counter) are ordered/stateful, not because generation is serial.
 *
 * <pre>
 * Worker 0 → chunks 0,N,2N…  → queue[0] →\
 * Worker 1 → chunks 1,N+1…   → queue[1] → } → Writer (merge in chunk order) → Serialize → Destination
 * Worker N-1 → chunks N-1…    → queue[N-1] →/
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
      // Seed by global record index so output matches the multi-threaded path byte-for-byte.
      random.setSeed(randomProvider.deriveRecordSeed(i));
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
          "Worker Future is intentionally not stored; the first worker failure is captured in "
              + "workerError, which interrupts the writer and is rethrown after awaitTermination")
  @SuppressWarnings({"PMD.AvoidCatchingGenericException", "java:S3776"})
  private <P> void runMultiThreaded(long count, Function<Random, P> produce, Consumer<P> consume)
      throws InterruptedException {
    RandomProvider randomProvider = new RandomProvider(masterSeed);

    // Work is split into fixed-size chunks of global record indices: chunk c covers records
    // [c*chunkSize, (c+1)*chunkSize) and is owned by worker (c % activeWorkers). Each worker
    // produces
    // its chunks in ascending order into its OWN bounded queue; the writer merges the queues in
    // global chunk order (chunk c from queue c % activeWorkers). Since per-record seeding is keyed
    // on
    // the global index, this reconstructs byte-for-byte identical output regardless of thread
    // count,
    // with bounded memory and no reorder buffer.
    long totalChunks = (count + chunkSize - 1) / chunkSize;
    int activeWorkers = (int) Math.clamp(totalChunks, 1L, workerThreads);

    // Per-worker bounded queues provide backpressure: a worker blocks once it runs queueCapacity
    // ahead of the writer. queueCapacity is in records; convert to chunks and split across workers,
    // keeping at least 2 chunks per worker so generation and writing overlap.
    int perWorkerCapacity = Math.max(2, queueCapacity / chunkSize / activeWorkers);
    List<BlockingQueue<List<P>>> workerQueues = new ArrayList<>(activeWorkers);
    for (int i = 0; i < activeWorkers; i++) {
      workerQueues.add(new ArrayBlockingQueue<>(perWorkerCapacity));
    }

    // First worker failure (if any), used to abort the writer instead of letting it block forever.
    AtomicReference<Throwable> workerError = new AtomicReference<>();
    AtomicLong generated = new AtomicLong(0);
    long startTime = System.currentTimeMillis();

    // Single writer thread: pulls chunk c from its owning worker's queue, in ascending c, and
    // writes
    // it. Because each worker emits its chunks in order, this reconstructs the exact global order.
    Thread writerThread =
        new Thread(
            () -> {
              try {
                for (long c = 0; c < totalChunks; c++) {
                  List<P> chunk = workerQueues.get((int) (c % activeWorkers)).take();
                  for (P item : chunk) {
                    consume.accept(item);
                  }
                }
                log.debug("Writer thread finished");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("Writer thread interrupted (generation aborted)");
              } catch (Exception e) {
                log.error("Writer thread failed", e);
                throw new IllegalStateException("Writer thread failed", e);
              }
            },
            "writer-thread");
    writerThread.start();

    // Start worker threads
    ExecutorService workers = Executors.newFixedThreadPool(activeWorkers);

    for (int workerId = 0; workerId < activeWorkers; workerId++) {
      int finalWorkerId = workerId;
      int finalActiveWorkers = activeWorkers;
      BlockingQueue<List<P>> myQueue = workerQueues.get(workerId);

      workers.submit(
          () -> {
            try {
              generateWorkerRecords(
                  finalWorkerId,
                  finalActiveWorkers,
                  count,
                  totalChunks,
                  randomProvider,
                  produce,
                  myQueue,
                  new ProgressTracker(generated, count, startTime));
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              workerError.compareAndSet(null, e);
              log.error("Worker {} interrupted", finalWorkerId, e);
            } catch (Exception e) {
              workerError.compareAndSet(null, e);
              log.error("Worker {} failed", finalWorkerId, e);
            }
          });
    }

    // Shutdown workers and wait for completion
    workers.shutdown();
    boolean terminated = workers.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    if (!terminated) {
      log.warn("Worker threads did not terminate gracefully");
    }

    // If a worker failed, the writer may be blocked waiting for a chunk that will never arrive —
    // interrupt it, then surface the failure. Otherwise it drains exactly totalChunks and exits.
    Throwable failure = workerError.get();
    if (failure != null) {
      writerThread.interrupt();
      writerThread.join();
      throw new IllegalStateException("Parallel generation failed", failure);
    }
    writerThread.join();

    logProgress(count, count, startTime); // Final progress
    log.info(
        "Parallel generation complete: {} records generated by {} workers", count, activeWorkers);
  }

  /**
   * Shared progress state passed to every worker: the cross-worker generated counter, the overall
   * target count, and the run start time used for throughput logging.
   *
   * @param generated atomic counter for total generated records across all workers
   * @param totalCount total record count, for progress percentage
   * @param startTime run start time in milliseconds, for throughput calculation
   */
  private record ProgressTracker(AtomicLong generated, long totalCount, long startTime) {}

  /**
   * Generate the chunks assigned to a single worker. Worker {@code workerId} owns chunks {@code
   * workerId, workerId + activeWorkers, ...} (interleaved). Each record is seeded by its global
   * index via {@link RandomProvider#deriveRecordSeed(long)}, so the value at a given index does not
   * depend on which worker produced it — the property that makes output thread-count-invariant.
   *
   * @param <P> payload type carried worker → writer
   * @param workerId worker ID (0-based)
   * @param activeWorkers number of workers participating (chunk stride)
   * @param totalRecords total record count across all workers
   * @param totalChunks total number of chunks
   * @param randomProvider provider of the thread-local Random (reseeded per record)
   * @param produce builds (and optionally serializes) one payload from a Random
   * @param queue this worker's own queue for handing off its chunks, in ascending chunk order
   * @param progress shared progress state (total generated counter, target count, start time)
   * @throws InterruptedException if interrupted
   */
  @SuppressWarnings("java:S107")
  private <P> void generateWorkerRecords(
      int workerId,
      int activeWorkers,
      long totalRecords,
      long totalChunks,
      RandomProvider randomProvider,
      Function<Random, P> produce,
      BlockingQueue<List<P>> queue,
      ProgressTracker progress)
      throws InterruptedException {

    // Thread-local Random, reseeded per record from the record's global index.
    Random random = randomProvider.getRandom();
    long workerGenerated = 0;

    for (long c = workerId; c < totalChunks; c += activeWorkers) {
      long start = c * chunkSize;
      long end = Math.min(start + chunkSize, totalRecords);
      List<P> chunk = new ArrayList<>((int) (end - start));

      for (long globalIndex = start; globalIndex < end; globalIndex++) {
        random.setSeed(randomProvider.deriveRecordSeed(globalIndex));
        chunk.add(produce.apply(random));
        workerGenerated++;

        if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
          log.trace("Worker {} generated record at index {}", workerId, globalIndex);
        }
      }

      queue.put(chunk); // blocks if queue is full - backpressure
      recordChunkProgress(progress, chunk.size());
    }

    log.debug("Worker {} completed: generated {} records", workerId, workerGenerated);
    workerCleanup.run();
  }

  /**
   * Updates the shared generated-record counter once per flushed chunk instead of once per record,
   * avoiding cross-worker CAS contention on the hot path, and logs progress when the running total
   * crosses a {@code logBatchSize} boundary.
   *
   * @param progress shared progress state
   * @param chunkCount number of records in the just-flushed chunk
   */
  private void recordChunkProgress(ProgressTracker progress, int chunkCount) {
    long before = progress.generated().getAndAdd(chunkCount);
    long after = before + chunkCount;
    if (before / logBatchSize != after / logBatchSize) {
      logProgress(after, progress.totalCount(), progress.startTime());
    }
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
