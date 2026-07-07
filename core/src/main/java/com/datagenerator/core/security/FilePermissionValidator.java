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
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates file permissions for configuration and seed files.
 *
 * <p>On Unix-like systems, warns when configuration files are readable by group or others, and
 * fails fast when seed files have the same permissive permissions (seed files may contain sensitive
 * values and should be owner-only: {@code chmod 600}).
 *
 * <p>On Windows, POSIX permissions are not available — all checks are silently skipped.
 *
 * <p><b>Note:</b> this is a best-effort advisory check, not a TOCTOU-safe gate. The permission read
 * and any later open/read of the file are separate operations on the path, so a symlink swap or
 * permission change between the two is not prevented. The goal is to flag obviously misconfigured
 * files, not to guarantee the file is untampered with at the moment it is actually consumed.
 */
@Slf4j
public class FilePermissionValidator {

  private static final boolean IS_POSIX =
      !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

  /**
   * Warns if the given configuration file is readable by group or others.
   *
   * <p>This is a best-effort advisory check performed on the path; see the class-level note for why
   * it does not eliminate TOCTOU races.
   *
   * @param configFile path to the configuration file
   */
  public void validateConfigFile(Path configFile) {
    if (!IS_POSIX) return;
    try {
      Set<PosixFilePermission> perms = readPerms(configFile);
      if (!perms.isEmpty() && isWorldReadable(perms)) {
        log.warn(
            "Configuration file {} has permissive permissions (readable by group or others). "
                + "Consider restricting to owner-only: chmod 640 {}",
            configFile,
            configFile);
      }
    } catch (IOException e) {
      log.debug("Could not read permissions for config file {}: {}", configFile, e.getMessage());
    }
  }

  /**
   * Fails fast if the given seed file is readable by group or others.
   *
   * <p>Seed files may contain sensitive values and must be restricted to owner read/write only
   * ({@code chmod 600}).
   *
   * @param seedFile path to the seed file
   * @throws SecurityException if the seed file has insecure permissions
   */
  public void validateSeedFile(Path seedFile) {
    validateSecretFile(seedFile, "Seed file");
  }

  /**
   * Fails fast if the given secret-bearing file is readable by group or others.
   *
   * <p>Used for any file that may hold sensitive material (seed files, AES encryption key files,
   * etc.) which must be restricted to owner read/write only ({@code chmod 600}).
   *
   * <p>This is a best-effort advisory check performed on the path; see the class-level note for why
   * it does not eliminate TOCTOU races.
   *
   * @param secretFile path to the secret-bearing file
   * @param description human-readable description of the file, used in the exception message (e.g.
   *     {@code "Seed file"}, {@code "Encryption key file"})
   * @throws SecurityException if the file has insecure permissions
   */
  public void validateSecretFile(Path secretFile, String description) {
    if (!IS_POSIX) return;
    try {
      Set<PosixFilePermission> perms = readPerms(secretFile);
      if (!perms.isEmpty() && isWorldReadable(perms)) {
        throw new SecurityException(
            description
                + " has insecure permissions (readable by group or others). "
                + "Restrict to owner-only: chmod 600 "
                + secretFile);
      }
    } catch (SecurityException e) {
      throw e;
    } catch (IOException e) {
      log.debug(
          "Could not read permissions for {} {}: {}", description, secretFile, e.getMessage());
    }
  }

  /**
   * Reads POSIX permissions for the given path.
   *
   * <p>This is a plain, single-shot attribute lookup on the path — not synchronized with any later
   * open or read of the same file. Returns an empty set when POSIX attributes are unavailable (e.g.
   * on a filesystem without POSIX support).
   *
   * @throws IOException if the attributes cannot be read (e.g. the file does not exist)
   */
  private static Set<PosixFilePermission> readPerms(Path path) throws IOException {
    PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
    if (view == null) {
      return Collections.emptySet();
    }
    return view.readAttributes().permissions();
  }

  private static boolean isWorldReadable(Set<PosixFilePermission> perms) {
    return perms.contains(PosixFilePermission.GROUP_READ)
        || perms.contains(PosixFilePermission.OTHERS_READ);
  }
}
