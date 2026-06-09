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

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FilePermissionValidatorTest {

  private static final String SEED_FILE = "seed.txt";

  @TempDir Path tempDir;

  // ── Config file tests (Unix only) ────────────────────────────────────────

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @ValueSource(strings = {"rw-------", "rw-r-----", "rw-r--r--"})
  void shouldNotThrowForConfigFileWithAnyReadPermission(String perms) throws IOException {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path file = createFileWithPermissions("config.yaml", perms);
    assertThatNoException().isThrownBy(() -> validator.validateConfigFile(file));
  }

  @Test
  void shouldSilentlySkipConfigFileWhenItDoesNotExist() {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path missing = tempDir.resolve("nonexistent.yaml");
    assertThatNoException().isThrownBy(() -> validator.validateConfigFile(missing));
  }

  // ── Seed file tests (Unix only) ──────────────────────────────────────────

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldPassWhenSeedFileIsOwnerOnly() throws IOException {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path file = createFileWithPermissions(SEED_FILE, "rw-------");
    assertThatNoException().isThrownBy(() -> validator.validateSeedFile(file));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldFailWhenSeedFileIsGroupReadable() throws IOException {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path file = createFileWithPermissions(SEED_FILE, "rw-r-----");
    assertThatThrownBy(() -> validator.validateSeedFile(file))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("insecure permissions")
        .hasMessageContaining("chmod 600");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldFailWhenSeedFileIsWorldReadable() throws IOException {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path file = createFileWithPermissions(SEED_FILE, "rw-r--r--");
    assertThatThrownBy(() -> validator.validateSeedFile(file))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("insecure permissions");
  }

  @Test
  void shouldSilentlySkipSeedFileWhenItDoesNotExist() {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path missing = tempDir.resolve("nonexistent.seed");
    assertThatNoException().isThrownBy(() -> validator.validateSeedFile(missing));
  }

  // ── Windows: all checks silently skipped ─────────────────────────────────

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldSkipAllChecksOnWindows() throws IOException {
    FilePermissionValidator validator = new FilePermissionValidator();
    Path file = Files.createTempFile(tempDir, "test", ".yaml");
    assertThatNoException().isThrownBy(() -> validator.validateConfigFile(file));
    assertThatNoException().isThrownBy(() -> validator.validateSeedFile(file));
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private Path createFileWithPermissions(String name, String posixString) throws IOException {
    Set<PosixFilePermission> perms = PosixFilePermissions.fromString(posixString);
    Path file =
        Files.createFile(tempDir.resolve(name), PosixFilePermissions.asFileAttribute(perms));
    return file;
  }
}
