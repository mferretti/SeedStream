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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class PathValidatorTest {

  private static final String CONTEXT = "file destination output path";

  @TempDir Path tempDir;

  // ── validate (read-oriented, existing behavior) ──────────────────────────

  @Test
  void shouldRejectBlankPath() {
    assertThatThrownBy(() -> PathValidator.validate("", null, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null or blank");
  }

  // ── validateOutput ─────────────────────────────────────────────────────

  @Test
  void shouldRejectBlankOutputPath() {
    assertThatThrownBy(() -> PathValidator.validateOutput("", CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null or blank");
  }

  @Test
  void shouldRejectNullOutputPath() {
    assertThatThrownBy(() -> PathValidator.validateOutput(null, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null or blank");
  }

  @Test
  void shouldAllowOutputPathThatDoesNotExistYet() {
    Path target = tempDir.resolve("new-output.json");
    assertThatNoException()
        .isThrownBy(
            () -> {
              Path resolved = PathValidator.validateOutput(target.toString(), CONTEXT);
              assertThat(resolved).isEqualTo(target.normalize());
            });
  }

  @Test
  void shouldAllowOutputPathThatIsAnExistingRegularFile() throws IOException {
    Path target = tempDir.resolve("existing.json");
    Files.writeString(target, "old content");

    assertThatNoException()
        .isThrownBy(() -> PathValidator.validateOutput(target.toString(), CONTEXT));
  }

  @Test
  void shouldRejectDirectoryAsOutputTarget() throws IOException {
    Path dir = tempDir.resolve("a-directory");
    Files.createDirectory(dir);
    String dirPath = dir.toString();

    assertThatThrownBy(() -> PathValidator.validateOutput(dirPath, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a regular file");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectSymlinkTarget() throws IOException {
    Path realFile = tempDir.resolve("real.json");
    Files.writeString(realFile, "sensitive content");
    Path symlink = tempDir.resolve("link.json");
    Files.createSymbolicLink(symlink, realFile);
    String symlinkPath = symlink.toString();

    assertThatThrownBy(() -> PathValidator.validateOutput(symlinkPath, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("symlink");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectSymlinkToDirectoryTarget() throws IOException {
    Path realDir = tempDir.resolve("real-dir");
    Files.createDirectory(realDir);
    Path symlink = tempDir.resolve("dir-link");
    Files.createSymbolicLink(symlink, realDir);
    String symlinkPath = symlink.toString();

    assertThatThrownBy(() -> PathValidator.validateOutput(symlinkPath, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("symlink");
  }
}
