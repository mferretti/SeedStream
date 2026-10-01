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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class FilePermissionValidatorTest {

  private static final String SEED_FILE = "seed.txt";

  @TempDir Path tempDir;

  private Logger validatorLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    validatorLogger = (Logger) LoggerFactory.getLogger(FilePermissionValidator.class);
    appender = new ListAppender<>();
    appender.start();
    validatorLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    validatorLogger.detachAppender(appender);
    appender.stop();
  }

  private List<ILoggingEvent> warnings() {
    return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @ValueSource(strings = {"rw-r-----", "rw-r--r--", "rw----r--", "r--r--r--"})
  void shouldWarnWhenConfigFileIsGroupOrOtherReadable(String perms) throws IOException {
    Path file = createFileWithPermissions("config.yaml", perms);

    new FilePermissionValidator().validateConfigFile(file);

    assertThat(warnings())
        .singleElement()
        .satisfies(
            e ->
                assertThat(e.getFormattedMessage())
                    .contains("permissive permissions")
                    .contains(file.toString()));
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @ValueSource(strings = {"rw-------", "r--------", "rw--w----", "rwx------", "-w------x"})
  void shouldNotWarnWhenConfigFileIsNotGroupOrOtherReadable(String perms) throws IOException {
    Path file = createFileWithPermissions("config.yaml", perms);

    new FilePermissionValidator().validateConfigFile(file);

    assertThat(warnings()).isEmpty();
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldWarnAfterPermissionsAreLoosenedOnExistingFile() throws IOException {
    Path file = createFileWithPermissions("config.yaml", "rw-------");
    FilePermissionValidator validator = new FilePermissionValidator();
    validator.validateConfigFile(file);
    assertThat(warnings()).isEmpty();

    // Deliberately insecure, proves the warning fires
    Files.setPosixFilePermissions( // nosemgrep
        file, PosixFilePermissions.fromString("rw-rw-rw-"));
    validator.validateConfigFile(file);

    assertThat(warnings()).hasSize(1);
  }

  @Test
  void shouldNotWarnWhenConfigFileDoesNotExist() {
    new FilePermissionValidator().validateConfigFile(tempDir.resolve("missing.yaml"));

    assertThat(warnings()).isEmpty();
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @ValueSource(strings = {"rw----r--", "r--r--r--", "rwxr-x---"})
  void shouldFailSecretFileReadableByOthersOrGroupAndNameFileInMessage(String perms)
      throws IOException {
    Path file = createFileWithPermissions("aes.key", perms);

    assertThatThrownBy(() -> new FilePermissionValidator().validateSecretFile(file, "Key file"))
        .isInstanceOf(SecurityException.class)
        .hasMessageStartingWith("Key file has insecure permissions")
        .hasMessageContaining("chmod 600 " + file);
  }

  @ParameterizedTest
  @DisabledOnOs(OS.WINDOWS)
  @ValueSource(strings = {"rw-------", "r--------", "rw--w----"})
  void shouldAcceptSecretFileNotReadableByGroupOrOthers(String perms) throws IOException {
    Path file = createFileWithPermissions("aes.key", perms);

    assertThatNoException()
        .isThrownBy(() -> new FilePermissionValidator().validateSecretFile(file, "Key file"));
    assertThat(warnings()).isEmpty();
  }

  @Test
  @EnabledOnOs(OS.WINDOWS)
  void shouldNotWarnOnWindowsEvenForReadableFile() throws IOException {
    Path file = Files.createTempFile(tempDir, "cfg", ".yaml");

    new FilePermissionValidator().validateConfigFile(file);

    assertThat(warnings()).isEmpty();
  }

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
    return Files.createFile(tempDir.resolve(name), PosixFilePermissions.asFileAttribute(perms));
  }
}
