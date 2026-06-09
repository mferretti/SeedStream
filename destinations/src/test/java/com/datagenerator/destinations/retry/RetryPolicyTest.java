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

package com.datagenerator.destinations.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.destinations.DestinationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  @Test
  void shouldSucceedOnFirstAttempt() {
    AtomicInteger calls = new AtomicInteger();
    RetryPolicy policy = RetryPolicy.of(3, 0);

    policy.execute("op", calls::incrementAndGet);

    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void shouldRetryAndSucceedAfterTransientFailure() {
    AtomicInteger calls = new AtomicInteger();
    RetryPolicy policy = RetryPolicy.of(3, 0);

    policy.execute(
        "op",
        () -> {
          if (calls.incrementAndGet() < 3) throw new IllegalStateException("transient");
        });

    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void shouldExhaustAttemptsAndThrowDestinationException() {
    AtomicInteger calls = new AtomicInteger();
    RetryPolicy policy = RetryPolicy.of(3, 0);

    assertThatThrownBy(
            () ->
                policy.execute(
                    "failing op",
                    () -> {
                      calls.incrementAndGet();
                      throw new RuntimeException("always fails");
                    }))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("failed after 3 attempt");

    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void shouldNotRetryWhenMaxAttemptsIsOne() {
    AtomicInteger calls = new AtomicInteger();
    RetryPolicy policy = RetryPolicy.of(1, 0);

    assertThatThrownBy(
            () ->
                policy.execute(
                    "op",
                    () -> {
                      calls.incrementAndGet();
                      throw new RuntimeException("fail");
                    }))
        .isInstanceOf(DestinationException.class);

    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void shouldBehaveAsDisabledWhenUsingFactory() {
    AtomicInteger calls = new AtomicInteger();
    RetryPolicy policy = RetryPolicy.disabled();

    assertThatThrownBy(
            () ->
                policy.execute(
                    "op",
                    () -> {
                      calls.incrementAndGet();
                      throw new RuntimeException("fail");
                    }))
        .isInstanceOf(DestinationException.class);

    assertThat(calls.get()).isEqualTo(1);
    assertThat(policy.getMaxAttempts()).isEqualTo(1);
    assertThat(policy.getInitialDelayMs()).isZero();
  }

  @Test
  void shouldRestoreInterruptFlagOnInterruptedException() {
    RetryPolicy policy = RetryPolicy.of(3, 0);

    Thread.currentThread().interrupt(); // pre-set interrupt

    assertThatThrownBy(
            () ->
                policy.execute(
                    "op",
                    () -> {
                      throw new InterruptedException("interrupted");
                    }))
        .isInstanceOf(DestinationException.class)
        .hasMessageContaining("Interrupted");

    assertThat(Thread.interrupted()).isTrue(); // flag restored by retry policy
  }

  @Test
  void shouldRejectInvalidMaxAttempts() {
    assertThatThrownBy(() -> RetryPolicy.of(0, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts");
  }

  @Test
  void shouldRejectNegativeDelay() {
    assertThatThrownBy(() -> RetryPolicy.of(3, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("initialDelayMs");
  }
}
