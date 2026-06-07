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

import com.datagenerator.destinations.DestinationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes an operation with exponential-backoff retry on transient failures.
 *
 * <p>Retries any exception except {@link InterruptedException} (which restores the interrupt flag
 * and propagates immediately). After {@code maxAttempts} unsuccessful attempts the last exception
 * is wrapped in a {@link DestinationException}.
 *
 * <p>Backoff doubles on each attempt: {@code delay}, {@code delay×2}, {@code delay×4}, … Set {@code
 * initialDelayMs = 0} for instant retries (e.g. in unit tests).
 *
 * <p>Example: {@code RetryPolicy.of(3, 1000).execute("Kafka write", () -> producer.send(r).get())}
 */
@Slf4j
public final class RetryPolicy {

  private static final long MAX_DELAY_MS = 30_000L;

  /** A runnable that may throw any checked or unchecked exception. */
  @FunctionalInterface
  public interface CheckedRunnable {
    void run() throws Exception;
  }

  private final int maxAttempts;
  private final long initialDelayMs;

  private RetryPolicy(int attempts, long delayMs) {
    this.maxAttempts = attempts;
    this.initialDelayMs = delayMs;
  }

  /**
   * @param maxAttempts total attempts (including the first); must be ≥ 1
   * @param initialDelayMs delay before the first retry in milliseconds; must be ≥ 0
   */
  public static RetryPolicy of(int maxAttempts, long initialDelayMs) {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
    if (initialDelayMs < 0) throw new IllegalArgumentException("initialDelayMs must be >= 0");
    return new RetryPolicy(maxAttempts, initialDelayMs);
  }

  /** Single-attempt policy — equivalent to no retry. */
  public static RetryPolicy disabled() {
    return new RetryPolicy(1, 0);
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public long getInitialDelayMs() {
    return initialDelayMs;
  }

  /**
   * Execute {@code operation}, retrying on failure with exponential backoff.
   *
   * @param operationName human-readable name used in log messages and the final exception message
   * @param operation the operation to execute
   * @throws DestinationException if all attempts fail; cause is the last thrown exception
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  public void execute(String operationName, CheckedRunnable operation) {
    long delay = initialDelayMs;
    Exception lastException = null;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        operation.run();
        return;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new DestinationException("Interrupted during " + operationName, e);
      } catch (Exception e) {
        lastException = e;
        if (attempt < maxAttempts) {
          log.warn(
              "{} failed (attempt {}/{}), retrying in {}ms: {}",
              operationName,
              attempt,
              maxAttempts,
              delay,
              e.getMessage());
          sleepUninterruptibly(operationName, delay);
          delay = Math.min(delay * 2, MAX_DELAY_MS);
        }
      }
    }

    throw new DestinationException(
        operationName
            + " failed after "
            + maxAttempts
            + " attempt(s): "
            + lastException.getMessage(),
        lastException);
  }

  private void sleepUninterruptibly(String operationName, long ms) {
    if (ms <= 0) return;
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DestinationException("Interrupted during retry backoff for " + operationName, e);
    }
  }
}
