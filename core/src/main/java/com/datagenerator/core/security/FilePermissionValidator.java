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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
 */
@Slf4j
public class FilePermissionValidator {

  private static final boolean IS_POSIX =
      !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

  /**
   * Warns if the given configuration file is readable by group or others.
   *
   * <p>Opens the file before reading its POSIX attributes to eliminate the TOCTOU race that would
   * exist if a separate {@code Files.exists()} check preceded the attribute read.
   *
   * @param configFile path to the configuration file
   */
  public void validateConfigFile(Path configFile) {
    if (!IS_POSIX) return;
    try (FileChannel channel = FileChannel.open(configFile, StandardOpenOption.READ)) {
      Set<PosixFilePermission> perms = readPermsFromChannel(configFile);
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
   * <p>Opens the file before reading its POSIX attributes to eliminate the TOCTOU race that would
   * exist if a separate {@code Files.exists()} check preceded the attribute read.
   *
   * @param seedFile path to the seed file
   * @throws SecurityException if the seed file has insecure permissions
   */
  public void validateSeedFile(Path seedFile) {
    if (!IS_POSIX) return;
    try (FileChannel channel = FileChannel.open(seedFile, StandardOpenOption.READ)) {
      Set<PosixFilePermission> perms = readPermsFromChannel(seedFile);
      if (!perms.isEmpty() && isWorldReadable(perms)) {
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

  /**
   * Reads POSIX permissions atomically via an already-open FileChannel.
   *
   * <p>The channel is opened first so the JVM holds a file-descriptor reference to the inode.
   * Reading attributes via that same channel's path avoids a classic TOCTOU window (check-then-act
   * on the path string). Returns an empty set when POSIX attributes are unavailable.
   */
  private static Set<PosixFilePermission> readPermsFromChannel(Path path) {
    PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
    if (view == null) {
      return Collections.emptySet();
    }
    try {
      return view.readAttributes().permissions();
    } catch (IOException e) {
      return Collections.emptySet();
    }
  }

  private static boolean isWorldReadable(Set<PosixFilePermission> perms) {
    return perms.contains(PosixFilePermission.GROUP_READ)
        || perms.contains(PosixFilePermission.OTHERS_READ);
  }
}
