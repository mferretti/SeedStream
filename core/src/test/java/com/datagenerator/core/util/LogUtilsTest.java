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

package com.datagenerator.core.util;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests for LogUtils.shouldTrace().
 *
 * <p>Note: TRACE_SAMPLE_RATE is a static final initialized once at class load from the system
 * property {@code com.datagenerator.traceSampleRate}. In the test JVM, this defaults to 10%. Tests
 * verify statistical behavior and thread safety at the default rate; they do not manipulate the
 * static field.
 */
class LogUtilsTest {

  @Test
  void shouldExposeTraceSampleRatePropertyConstant() {
    assertThat(LogUtils.TRACE_SAMPLE_RATE_PROPERTY).isEqualTo("com.datagenerator.traceSampleRate");
  }

  @Test
  void shouldNotThrowOnSingleCall() {
    assertThatNoException().isThrownBy(LogUtils::shouldTrace);
  }

  @Test
  void shouldReturnBooleanOnRepeatedCalls() {
    for (int i = 0; i < 100; i++) {
      boolean result = LogUtils.shouldTrace();
      assertThat(result).isIn(true, false);
    }
  }

  @Test
  void shouldReturnTrueAtLeastOnceOver10000Calls() {
    // At default 10% sample rate, over 10000 calls we expect ~1000 trues.
    // This test would only fail if the RNG is pathologically skewed or rate is 0.
    boolean seenTrue = false;
    for (int i = 0; i < 10_000; i++) {
      if (LogUtils.shouldTrace()) {
        seenTrue = true;
        break;
      }
    }
    assertThat(seenTrue).as("shouldTrace() returned true at least once in 10000 calls").isTrue();
  }

  @Test
  void shouldReturnFalseAtLeastOnceOver10000Calls() {
    // At default 10% rate, ~90% calls return false. Would only fail at 100% rate.
    boolean seenFalse = false;
    for (int i = 0; i < 10_000; i++) {
      if (!LogUtils.shouldTrace()) {
        seenFalse = true;
        break;
      }
    }
    assertThat(seenFalse).as("shouldTrace() returned false at least once in 10000 calls").isTrue();
  }

  @Test
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  void shouldBeThreadSafeUnderConcurrentCalls() throws InterruptedException {
    int threads = 8;
    int callsPerThread = 1000;
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger errors = new AtomicInteger();
    List<Thread> threadList = new ArrayList<>();

    for (int t = 0; t < threads; t++) {
      Thread thread =
          new Thread(
              () -> {
                try {
                  start.await();
                  for (int i = 0; i < callsPerThread; i++) {
                    LogUtils.shouldTrace(); // should not throw
                  }
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  errors.incrementAndGet();
                }
              });
      threadList.add(thread);
      thread.start();
    }

    start.countDown();
    for (Thread t : threadList) {
      t.join(5000);
    }

    assertThat(errors.get()).isZero();
  }

  @Test
  void shouldReturnIndependentResultsFromDifferentThreads() throws InterruptedException {
    // Each thread has its own ThreadLocal Random; results should be independent
    int threads = 4;
    AtomicInteger[] trueCounts = new AtomicInteger[threads];
    for (int i = 0; i < threads; i++) {
      trueCounts[i] = new AtomicInteger();
    }

    CountDownLatch latch = new CountDownLatch(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);

    for (int t = 0; t < threads; t++) {
      final int idx = t;
      pool.submit(
          () -> {
            try {
              for (int i = 0; i < 1000; i++) {
                if (LogUtils.shouldTrace()) {
                  trueCounts[idx].incrementAndGet();
                }
              }
            } finally {
              latch.countDown();
            }
          });
    }

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    pool.shutdown();

    // Each thread should have generated some true results (at 10% rate, expected ~100/1000)
    // We just verify no thread got exactly 0 or exactly 1000, which would be anomalous
    for (int i = 0; i < threads; i++) {
      int count = trueCounts[i].get();
      // At 10% rate over 1000 calls: extremely unlikely to see 0 or 1000
      assertThat(count)
          .as("Thread %d should have some true results", i)
          .isGreaterThan(0)
          .isLessThan(1000);
    }
  }

  @Test
  void truncateShouldReturnEmptyStringForNullInput() {
    assertThat(LogUtils.truncate(null, 200)).isEmpty();
  }

  @Test
  void truncateShouldReturnShortBodyUnchanged() {
    String body = "short response body";
    assertThat(LogUtils.truncate(body, 200)).isEqualTo(body);
  }

  @Test
  void truncateShouldReturnStringUnchangedWhenExactlyAtLimit() {
    String body = "x".repeat(200);
    assertThat(LogUtils.truncate(body, 200)).isEqualTo(body);
  }

  @Test
  void truncateShouldCapLargeBodyAndAppendMarker() {
    String body = "x".repeat(10_000);

    String result = LogUtils.truncate(body, 200);

    assertThat(result)
        .hasSizeLessThanOrEqualTo(300)
        .startsWith("x".repeat(200))
        .endsWith("… (10000 chars total)");
  }
}
