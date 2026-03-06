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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GenerationEngineTest {

  @Test
  void shouldGenerateRecordsInSingleThreadForSmallJobs() throws InterruptedException {
    // Given: Record generator that returns sequential IDs
    AtomicInteger idCounter = new AtomicInteger(0);
    GenerationEngine.RecordGenerator recordGenerator =
        (random) -> Map.of("id", idCounter.incrementAndGet());

    // Capture written records
    List<Map<String, Object>> writtenRecords = new ArrayList<>();

    // When: Generate 100 records (below threshold)
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(writtenRecords::add)
            .masterSeed(12345L)
            .singleThreadedThreshold(1000) // Use single thread for < 1000
            .build();

    engine.generate(100);

    // Then: All records generated
    assertThat(writtenRecords).hasSize(100);
  }

  @Test
  void shouldGenerateRecordsInMultipleThreadsForLargeJobs() throws InterruptedException {
    // Given: Record generator that creates unique records
    AtomicInteger idCounter = new AtomicInteger(0);
    GenerationEngine.RecordGenerator recordGenerator =
        (random) -> Map.of("id", idCounter.incrementAndGet());

    // Thread-safe record storage
    List<Map<String, Object>> writtenRecords = new ArrayList<>();
    GenerationEngine.RecordWriter recordWriter =
        record -> {
          synchronized (writtenRecords) {
            writtenRecords.add(record);
          }
        };

    // When: Generate 5000 records (above threshold, should use multiple threads)
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(recordWriter)
            .masterSeed(12345L)
            .workerThreads(4)
            .singleThreadedThreshold(1000)
            .build();

    engine.generate(5000);

    // Then: All records generated
    assertThat(writtenRecords).hasSize(5000);
  }

  @Test
  void shouldHandleZeroRecordCount() throws InterruptedException {
    // Given: Record generator
    GenerationEngine.RecordGenerator recordGenerator = (random) -> Map.of("id", 1);

    List<Map<String, Object>> writtenRecords = new ArrayList<>();

    // When: Generate 0 records
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(writtenRecords::add)
            .masterSeed(12345L)
            .build();

    engine.generate(0);

    // Then: No records generated
    assertThat(writtenRecords).isEmpty();
  }

  @Test
  void shouldUseDeterministicSeedForReproducibility() throws InterruptedException {
    // Given: Real generator with deterministic behavior
    GenerationEngine.RecordGenerator recordGenerator =
        (random) -> {
          int value = random.nextInt(1000);
          return Map.of("value", value);
        };

    // First run
    List<Map<String, Object>> firstRun = new ArrayList<>();
    GenerationEngine engine1 =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(firstRun::add)
            .masterSeed(12345L)
            .build();

    engine1.generate(100);

    // Second run with same seed
    List<Map<String, Object>> secondRun = new ArrayList<>();
    GenerationEngine engine2 =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(secondRun::add)
            .masterSeed(12345L)
            .build();

    engine2.generate(100);

    // Then: Both runs should produce identical data
    assertThat(firstRun).isEqualTo(secondRun);
  }

  @Test
  void shouldHandleDifferentWorkerThreadCounts() throws InterruptedException {
    // Given: Record generator
    AtomicInteger idCounter = new AtomicInteger(0);
    GenerationEngine.RecordGenerator recordGenerator =
        (random) -> Map.of("id", idCounter.incrementAndGet());

    // Thread-safe record storage
    List<Map<String, Object>> writtenRecords = new ArrayList<>();
    GenerationEngine.RecordWriter recordWriter =
        record -> {
          synchronized (writtenRecords) {
            writtenRecords.add(record);
          }
        };

    // When: Generate with 8 worker threads
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(recordWriter)
            .masterSeed(12345L)
            .workerThreads(8)
            .singleThreadedThreshold(100)
            .build();

    engine.generate(1000);

    // Then: All records generated
    assertThat(writtenRecords).hasSize(1000);
  }

  @Test
  void shouldProvideBackpressureWhenQueueIsFull() throws InterruptedException {
    // Given: Slow writer that simulates backpressure
    AtomicInteger writeCount = new AtomicInteger(0);
    GenerationEngine.RecordWriter slowWriter =
        record -> {
          writeCount.incrementAndGet();
          try {
            Thread.sleep(1); // Simulate slow write
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        };

    GenerationEngine.RecordGenerator recordGenerator = (random) -> Map.of("id", 1);

    // When: Generate with small queue capacity
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(slowWriter)
            .masterSeed(12345L)
            .workerThreads(4)
            .queueCapacity(10) // Small queue
            .singleThreadedThreshold(100)
            .build();

    engine.generate(500);

    // Then: All records eventually written (backpressure handled)
    assertThat(writeCount.get()).isEqualTo(500);
  }

  @Test
  void shouldDistributeWorkEvenlyAcrossWorkers() throws InterruptedException {
    // Given: Track which worker generated which records
    Map<Long, Integer> threadCounts = new ConcurrentHashMap<>();

    GenerationEngine.RecordGenerator recordGenerator =
        (random) -> {
          long threadId = Thread.currentThread().getId();
          threadCounts.merge(threadId, 1, Integer::sum);
          return Map.of("thread", threadId);
        };

    List<Map<String, Object>> writtenRecords = new ArrayList<>();
    GenerationEngine.RecordWriter recordWriter =
        record -> {
          synchronized (writtenRecords) {
            writtenRecords.add(record);
          }
        };

    // When: Generate with 4 workers
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(recordGenerator)
            .recordWriter(recordWriter)
            .masterSeed(12345L)
            .workerThreads(4)
            .singleThreadedThreshold(100)
            .build();

    engine.generate(1000);

    // Then: Work distributed across multiple threads
    assertThat(threadCounts).hasSizeGreaterThanOrEqualTo(4);
    assertThat(writtenRecords).hasSize(1000);

    // Verify roughly even distribution (each worker gets ~250 records)
    threadCounts.values().forEach(count -> assertThat(count).isBetween(200, 300));
  }
}
