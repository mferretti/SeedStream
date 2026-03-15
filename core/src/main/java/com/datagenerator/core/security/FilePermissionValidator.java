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
import java.nio.file.attribute.PosixFilePermission;
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
 */
@Slf4j
public class FilePermissionValidator {

  private static final boolean IS_POSIX =
      !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

  /**
   * Warns if the given configuration file is readable by group or others.
   *
   * @param configFile path to the configuration file
   */
  public void validateConfigFile(Path configFile) {
    if (!IS_POSIX || !Files.exists(configFile)) {
      return;
    }
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(configFile);
      if (perms.contains(PosixFilePermission.GROUP_READ)
          || perms.contains(PosixFilePermission.OTHERS_READ)) {
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
    if (!IS_POSIX || !Files.exists(seedFile)) {
      return;
    }
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(seedFile);
      if (perms.contains(PosixFilePermission.GROUP_READ)
          || perms.contains(PosixFilePermission.OTHERS_READ)) {
        throw new SecurityException(
            "Seed file has insecure permissions (readable by group or others). "
                + "Restrict to owner-only: chmod 600 "
                + seedFile);
      }
    } catch (SecurityException e) {
      throw e;
    } catch (IOException e) {
      log.debug("Could not read permissions for seed file {}: {}", seedFile, e.getMessage());
    }
  }
}
