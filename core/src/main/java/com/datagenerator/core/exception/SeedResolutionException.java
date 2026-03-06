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

package com.datagenerator.core.exception;

/**
 * Thrown when seed resolution fails.
 *
 * <p>This exception occurs when the {@link com.datagenerator.core.seed.SeedResolver} cannot obtain
 * a valid seed value from the configured source.
 *
 * <p><b>Common Causes:</b>
 *
 * <ul>
 *   <li><b>File seed:</b> Seed file doesn't exist or isn't readable
 *   <li><b>Environment seed:</b> Environment variable not set or empty
 *   <li><b>Remote seed:</b> API endpoint unreachable, authentication failure, or invalid response
 *   <li><b>Format errors:</b> Seed value not parseable as a long integer
 * </ul>
 *
 * <p><b>Resolution Steps:</b>
 *
 * <ol>
 *   <li>Verify seed file path is correct and file exists: {@code ls -la /path/to/seed.txt}
 *   <li>Check environment variable is set: {@code echo $SEED_VAR_NAME}
 *   <li>For remote seeds, verify API endpoint is accessible and credentials are valid
 *   <li>Ensure seed value is a valid long integer (no decimals, letters, or special characters)
 *   <li>As fallback, use embedded seed or CLI --seed override
 * </ol>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // File seed that doesn't exist
 * SeedConfig config = new FileSeedConfig("/nonexistent/seed.txt");
 * // Throws: SeedResolutionException: Failed to read seed from file: /nonexistent/seed.txt
 *
 * // Environment variable not set
 * SeedConfig config = new EnvSeedConfig("MISSING_VAR");
 * // Throws: SeedResolutionException: Environment variable not found: MISSING_VAR
 * </pre>
 *
 * @see com.datagenerator.core.seed.SeedResolver
 * @see com.datagenerator.core.seed.SeedConfig
 * @since 1.0
 */
public class SeedResolutionException extends RuntimeException {
  /**
   * Constructs a new seed resolution exception with the specified detail message.
   *
   * @param message the detail message explaining what went wrong
   */
  public SeedResolutionException(String message) {
    super(message);
  }

  /**
   * Constructs a new seed resolution exception with the specified detail message and cause.
   *
   * @param message the detail message explaining what went wrong
   * @param cause the underlying cause (e.g., IOException, HttpException)
   */
  public SeedResolutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
