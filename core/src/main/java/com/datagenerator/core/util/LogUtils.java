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

import java.util.Random;

/**
 * Utility class for logging operations with sampling support.
 *
 * <p>This class provides methods to control TRACE-level logging volume through statistical
 * sampling. When generating large datasets, full TRACE logging can produce overwhelming output.
 * Sampling allows debugging with manageable log volume.
 *
 * <p><b>Usage Pattern:</b>
 *
 * <pre>
 * if (log.isTraceEnabled() &amp;&amp; LogUtils.shouldTrace()) {
 *   log.trace("Generated record: {}", record);
 * }
 * </pre>
 *
 * <p><b>Configuration:</b> The trace sample rate is controlled via system property {@code
 * com.datagenerator.traceSampleRate} (1-100, percentage). Default is 10% if not specified.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Each thread uses its own Random instance via
 * ThreadLocal for sampling decisions.
 *
 * @since 1.0
 */
public final class LogUtils {

  /** System property name for trace sampling rate configuration. */
  public static final String TRACE_SAMPLE_RATE_PROPERTY = "com.datagenerator.traceSampleRate";

  /** Default trace sample rate (percentage) if not configured. */
  private static final int DEFAULT_SAMPLE_RATE = 10;

  /**
   * Configured trace sample rate (1-100 percentage).
   *
   * <p>Initialized once from system property when class is loaded. This happens after {@code
   * ExecuteCommand.configureLoggingLevel()} sets the property, so we get the correct configured
   * value.
   */
  private static final int TRACE_SAMPLE_RATE;

  static {
    // Read system property once at class initialization
    String propertyValue = System.getProperty(TRACE_SAMPLE_RATE_PROPERTY);
    int rate;

    if (propertyValue == null || propertyValue.isEmpty()) {
      rate = DEFAULT_SAMPLE_RATE;
    } else {
      try {
        rate = Integer.parseInt(propertyValue);
        // Clamp to valid range 1-100
        rate = Math.max(1, Math.min(100, rate));
      } catch (NumberFormatException e) {
        // Invalid value, use default
        rate = DEFAULT_SAMPLE_RATE;
      }
    }

    // Assign final variable exactly once
    TRACE_SAMPLE_RATE = rate;
  }

  /**
   * Thread-local Random instance for sampling decisions.
   *
   * <p>Each thread gets its own Random to avoid contention. We don't need cryptographic randomness
   * or seed coordination here - just statistical sampling.
   */
  private static final ThreadLocal<Random> THREAD_LOCAL_RANDOM =
      ThreadLocal.withInitial(Random::new);

  // Prevent instantiation
  private LogUtils() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Determines whether a TRACE log statement should be executed based on sampling rate.
   *
   * <p>Uses statistical sampling to reduce log volume. The sample rate is configured via system
   * property {@code com.datagenerator.traceSampleRate}:
   *
   * <ul>
   *   <li><b>100</b> - Log everything (no sampling)
   *   <li><b>10</b> - Log approximately 10% of calls (default)
   *   <li><b>1</b> - Log approximately 1% of calls
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * // Only execute TRACE log if sampling says yes
   * if (log.isTraceEnabled() &amp;&amp; LogUtils.shouldTrace()) {
   *   log.trace("Expensive log message: {}", computeExpensiveData());
   * }
   * </pre>
   *
   * <p><b>Thread Safety:</b> Uses thread-local Random, safe for concurrent calls.
   *
   * <p><b>Performance:</b> Hot path optimized - no system property lookup, no parsing, just a
   * simple comparison and optional random number generation.
   *
   * @return true if the TRACE log should be executed, false to skip it
   */
  public static boolean shouldTrace() {
    // Always trace if sample rate is 100%
    if (TRACE_SAMPLE_RATE >= 100) {
      return true;
    }

    // Never trace if sample rate is 0 or negative (shouldn't happen, but defensive)
    if (TRACE_SAMPLE_RATE <= 0) {
      return false;
    }

    // Statistical sampling: generate random number 0-99, check if < sampleRate
    Random random = THREAD_LOCAL_RANDOM.get();
    return random.nextInt(100) < TRACE_SAMPLE_RATE;
  }
}
