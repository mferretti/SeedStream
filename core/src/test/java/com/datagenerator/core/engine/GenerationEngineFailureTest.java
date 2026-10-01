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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Failure-path behaviour of {@link GenerationEngine}: propagation, hooks, per-thread state. */
class GenerationEngineFailureTest {

  private static final Duration HANG_GUARD = Duration.ofSeconds(20);
  private static final int MULTI_COUNT = 5000; // 20 chunks of 256 -> 4 active workers
  private static final int WORKERS = 4;

  private static GenerationEngine.GenerationEngineBuilder multiThreaded() {
    return GenerationEngine.builder()
        .masterSeed(7L)
        .workerThreads(WORKERS)
        .queueCapacity(10) // tiny queue: workers hit back-pressure immediately
        .singleThreadedThreshold(1);
  }

  private static GenerationEngine.RecordGenerator failingAt(long failIndex, RuntimeException boom) {
    return random -> {
      if (RecordIndex.current() == failIndex) {
        throw boom;
      }
      return Map.of("i", RecordIndex.current());
    };
  }

  // ── worker failure propagation (multi-threaded) ───────────────────────────

  @Test
  void shouldPropagateWorkerExceptionAsCauseAndStopWritingWhenWorkerFailsMidRun() {
    RuntimeException boom = new RuntimeException("generator blew up at 1500");
    List<Map<String, Object>> written = new CopyOnWriteArrayList<>();
    GenerationEngine engine =
        multiThreaded().recordGenerator(failingAt(1500, boom)).recordWriter(written::add).build();

    assertTimeoutPreemptively(
        HANG_GUARD,
        () ->
            assertThatThrownBy(() -> engine.generate(MULTI_COUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Parallel generation failed")
                .hasCause(boom));

    // Chunk 5 (records 1280..1535) never completes, so the writer can never get past chunk 4.
    assertThat(written).hasSizeLessThanOrEqualTo(1500);
  }

  @Test
  void shouldNotHangWhenFirstChunkFailsWhileWriterIsBlockedWaitingForIt() {
    RuntimeException boom = new RuntimeException("fails on very first record");
    GenerationEngine engine =
        multiThreaded().recordGenerator(failingAt(0, boom)).recordWriter(r -> {}).build();

    assertTimeoutPreemptively(
        HANG_GUARD,
        () ->
            assertThatThrownBy(() -> engine.generate(MULTI_COUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(boom));
  }

  @Test
  void shouldPropagateSerializerExceptionRaisedOnWorkerThread() {
    RuntimeException boom = new RuntimeException("serializer failed");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(random -> Map.of("i", RecordIndex.current()))
            .recordSerializer(
                data -> {
                  if ((long) data.get("i") == 700) {
                    throw boom;
                  }
                  return "x".getBytes(StandardCharsets.UTF_8);
                })
            .serializedWriter(bytes -> {})
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD,
        () ->
            assertThatThrownBy(() -> engine.generate(MULTI_COUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(boom));
  }

  @Test
  void shouldPropagateChunkFolderExceptionRaisedOnWorkerThread() {
    RuntimeException boom = new RuntimeException("fold failed");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(random -> Map.of("i", 1))
            .recordSerializer(data -> new byte[] {1})
            .serializedWriter(bytes -> {})
            .chunkFolder(
                payloads -> {
                  throw boom;
                })
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD,
        () ->
            assertThatThrownBy(() -> engine.generate(MULTI_COUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(boom));
  }

  @Test
  void shouldPropagateWorkerInitFailureWithoutHanging() {
    RuntimeException boom = new RuntimeException("init failed");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(r -> {})
            .workerInit(
                () -> {
                  throw boom;
                })
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD,
        () ->
            assertThatThrownBy(() -> engine.generate(MULTI_COUNT))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(boom));
  }

  // ── single-threaded failure propagation ───────────────────────────────────

  @Test
  void shouldRethrowOriginalGeneratorExceptionUnwrappedOnSingleThreadedPath() {
    RuntimeException boom = new RuntimeException("single-threaded boom");
    List<Map<String, Object>> written = new ArrayList<>();
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(failingAt(30, boom))
            .recordWriter(written::add)
            .build();

    assertThatThrownBy(() -> engine.generate(100)).isSameAs(boom);

    assertThat(written).hasSize(30); // records 0..29 written, nothing after the failure
  }

  @Test
  void shouldRethrowOriginalWriterExceptionUnwrappedOnSingleThreadedPath() {
    RuntimeException boom = new RuntimeException("write failed");
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(
                r -> {
                  throw boom;
                })
            .build();

    assertThatThrownBy(() -> engine.generate(100)).isSameAs(boom);
  }

  // ── workerInit / workerCleanup contract ───────────────────────────────────

  @Test
  void shouldRunInitAndCleanupExactlyOncePerWorkerOnSuccessfulParallelRun() {
    AtomicInteger inits = new AtomicInteger();
    AtomicInteger cleanups = new AtomicInteger();
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(r -> {})
            .workerInit(inits::incrementAndGet)
            .workerCleanup(cleanups::incrementAndGet)
            .build();

    assertTimeoutPreemptively(HANG_GUARD, () -> engine.generate(MULTI_COUNT));

    assertThat(inits).hasValue(WORKERS);
    assertThat(cleanups).hasValue(WORKERS);
  }

  @Test
  void shouldRunCleanupOncePerWorkerEvenWhenAWorkerFails() {
    AtomicInteger inits = new AtomicInteger();
    AtomicInteger cleanups = new AtomicInteger();
    RuntimeException boom = new RuntimeException("worker failure");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(failingAt(1500, boom))
            .recordWriter(r -> {})
            .workerInit(inits::incrementAndGet)
            .workerCleanup(cleanups::incrementAndGet)
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD, () -> assertThatThrownBy(() -> engine.generate(MULTI_COUNT)).hasCause(boom));

    assertThat(inits).hasValue(WORKERS);
    assertThat(cleanups).hasValue(WORKERS);
  }

  @Test
  void shouldRunCleanupOncePerWorkerEvenWhenWriterFails() {
    AtomicInteger cleanups = new AtomicInteger();
    RuntimeException boom = new RuntimeException("writer failure");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(
                r -> {
                  throw boom;
                })
            .workerCleanup(cleanups::incrementAndGet)
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD, () -> assertThatThrownBy(() -> engine.generate(MULTI_COUNT)).hasCause(boom));

    assertThat(cleanups).hasValue(WORKERS);
  }

  @Test
  void shouldRunInitOnceBeforeAndCleanupOnceAfterGenerationOnSingleThreadedPath() {
    List<String> events = new ArrayList<>();
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(
                random -> {
                  events.add("gen");
                  return Map.of("i", 1);
                })
            .recordWriter(r -> {})
            .workerInit(() -> events.add("init"))
            .workerCleanup(() -> events.add("cleanup"))
            .build();

    assertThatNoException().isThrownBy(() -> engine.generate(3));

    assertThat(events).containsExactly("init", "gen", "gen", "gen", "cleanup");
  }

  @Test
  void shouldRunCleanupOnceWhenSingleThreadedGenerationFails() {
    AtomicInteger inits = new AtomicInteger();
    AtomicInteger cleanups = new AtomicInteger();
    RuntimeException boom = new RuntimeException("boom");
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(failingAt(2, boom))
            .recordWriter(r -> {})
            .workerInit(inits::incrementAndGet)
            .workerCleanup(cleanups::incrementAndGet)
            .build();

    assertThatThrownBy(() -> engine.generate(10)).isSameAs(boom);

    assertThat(inits).hasValue(1);
    assertThat(cleanups).hasValue(1);
  }

  // ── RecordIndex per-thread state ──────────────────────────────────────────

  @Test
  void shouldExposeGlobalRecordIndexToGeneratorInOrderOnBothPaths() {
    for (int threshold : new int[] {Integer.MAX_VALUE, 1}) {
      List<Map<String, Object>> written = new CopyOnWriteArrayList<>();
      GenerationEngine engine =
          GenerationEngine.builder()
              .masterSeed(7L)
              .workerThreads(WORKERS)
              .singleThreadedThreshold(threshold)
              .recordGenerator(random -> Map.of("i", RecordIndex.current()))
              .recordWriter(written::add)
              .build();

      assertTimeoutPreemptively(HANG_GUARD, () -> engine.generate(MULTI_COUNT));

      assertThat(written).hasSize(MULTI_COUNT);
      for (int i = 0; i < MULTI_COUNT; i++) {
        assertThat(written.get(i))
            .as("record %d (threshold %d)", i, threshold)
            .containsEntry("i", (long) i);
      }
    }
  }

  @Test
  void shouldClearRecordIndexOnCallerThreadAfterFailedSingleThreadedRun() {
    RuntimeException boom = new RuntimeException("boom");
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(failingAt(17, boom))
            .recordWriter(r -> {})
            .build();

    assertThatThrownBy(() -> engine.generate(100)).isSameAs(boom);

    assertThat(RecordIndex.current()).isEqualTo(-1L);
  }

  @Test
  void shouldClearRecordIndexOnCallerThreadAfterSuccessfulSingleThreadedRun() {
    GenerationEngine engine =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(r -> {})
            .build();

    assertThatNoException().isThrownBy(() -> engine.generate(100));

    assertThat(RecordIndex.current()).isEqualTo(-1L);
  }

  @Test
  void shouldClearRecordIndexOnEveryWorkerThreadBeforeCleanupEvenWhenRunFails() {
    List<Long> indexSeenInCleanup = new CopyOnWriteArrayList<>();
    RuntimeException boom = new RuntimeException("worker failure");
    GenerationEngine engine =
        multiThreaded()
            .recordGenerator(failingAt(1500, boom))
            .recordWriter(r -> {})
            .workerCleanup(() -> indexSeenInCleanup.add(RecordIndex.current()))
            .build();

    assertTimeoutPreemptively(
        HANG_GUARD, () -> assertThatThrownBy(() -> engine.generate(MULTI_COUNT)).hasCause(boom));

    assertThat(indexSeenInCleanup).hasSize(WORKERS).containsOnly(-1L);
  }

  @Test
  void shouldNotLeakRecordIndexFromFailedRunIntoNextRunOnSameThread() {
    RuntimeException boom = new RuntimeException("boom");
    GenerationEngine failing =
        GenerationEngine.builder()
            .masterSeed(7L)
            .recordGenerator(failingAt(40, boom))
            .recordWriter(r -> {})
            .build();
    assertThatThrownBy(() -> failing.generate(100)).isSameAs(boom);

    List<Long> seenBeforeFirstRecord = new ArrayList<>();
    GenerationEngine next =
        GenerationEngine.builder()
            .masterSeed(7L)
            .workerInit(() -> seenBeforeFirstRecord.add(RecordIndex.current()))
            .recordGenerator(random -> Map.of("i", 1))
            .recordWriter(r -> {})
            .build();
    assertThatNoException().isThrownBy(() -> next.generate(5));

    assertThat(seenBeforeFirstRecord).containsExactly(-1L);
  }
}
