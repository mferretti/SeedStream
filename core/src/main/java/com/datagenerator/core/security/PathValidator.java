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

package com.datagenerator.core.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Validates file system paths supplied via configuration to prevent path-traversal attacks.
 *
 * <p>Callers that read files from user-supplied paths (seed files, key files, etc.) should call
 * {@link #validate} before opening the file. The method:
 *
 * <ol>
 *   <li>Rejects null/blank input.
 *   <li>Resolves the path against an optional base directory — if {@code baseDir} is non-null the
 *       canonicalized path must start with the canonicalized base directory.
 *   <li>Verifies the file exists and is a regular file (not a symlink to a directory, not a device
 *       node).
 * </ol>
 *
 * <p>When {@code baseDir} is {@code null} the containment check is skipped; canonicalization still
 * collapses {@code ..} sequences so the caller can see the real path in log messages.
 */
public final class PathValidator {

  private PathValidator() {}

  /**
   * Validates a user-supplied path.
   *
   * @param rawPath the path string from configuration
   * @param baseDir optional directory the path must reside in; {@code null} skips containment check
   * @param context human-readable context for the exception message (e.g. "seed file path")
   * @return the canonicalized {@link Path}, ready for I/O
   * @throws IllegalArgumentException if the path is null/blank, escapes the base directory, or is
   *     not a regular file
   */
  public static Path validate(String rawPath, Path baseDir, String context) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new IllegalArgumentException(context + " must not be null or blank");
    }

    Path resolved;
    try {
      resolved = Path.of(rawPath).toRealPath();
    } catch (IOException e) {
      // toRealPath requires the file to exist; if not, fall back to normalize for the containment
      // check and let the caller's own exists-check produce the clear error message.
      resolved = Path.of(rawPath).normalize().toAbsolutePath();
    }

    if (baseDir != null) {
      Path canonicalBase;
      try {
        canonicalBase = baseDir.toRealPath();
      } catch (IOException e) {
        canonicalBase = baseDir.normalize().toAbsolutePath();
      }
      if (!resolved.startsWith(canonicalBase)) {
        throw new IllegalArgumentException(
            context
                + " must be located within '"
                + canonicalBase
                + "'; path resolves to '"
                + resolved
                + "'");
      }
    }

    if (Files.exists(resolved) && !Files.isRegularFile(resolved)) {
      throw new IllegalArgumentException(context + " is not a regular file: '" + resolved + "'");
    }

    return resolved;
  }

  /**
   * Validates a user-supplied output path before it is opened for writing.
   *
   * <p>Unlike {@link #validate}, the target need not already exist — output destinations routinely
   * create a new file. This method is guardrail-level only (no base-directory confinement, callers
   * point generated data wherever they choose); it exists to stop the two concrete write-time
   * hazards:
   *
   * <ol>
   *   <li>Writing through a symlink, which can redirect generated data onto an arbitrary file the
   *       process can write (e.g. {@code ~/.bashrc}, another job's seed file).
   *   <li>Writing to a path that resolves to an existing non-regular file (device node, symlink to
   *       a directory, etc.).
   * </ol>
   *
   * @param rawPath the path string from configuration
   * @param context human-readable context for the exception message (e.g. "file destination output
   *     path")
   * @return the normalized {@link Path}, ready to be opened for writing
   * @throws IllegalArgumentException if the path is null/blank, resolves through a symlink, or
   *     resolves to an existing non-regular file
   */
  public static Path validateOutput(String rawPath, String context) {
    if (rawPath == null || rawPath.isBlank()) {
      throw new IllegalArgumentException(context + " must not be null or blank");
    }

    Path normalized = Path.of(rawPath).normalize();

    if (Files.isSymbolicLink(normalized)) {
      throw new IllegalArgumentException(
          context + " refuses to write through a symlink: '" + normalized + "'");
    }

    if (Files.exists(normalized) && !Files.isRegularFile(normalized)) {
      throw new IllegalArgumentException(context + " is not a regular file: '" + normalized + "'");
    }

    return normalized;
  }
}
