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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

  // ── validate: containment ────────────────────────────────────────────────

  private Path newBase() throws IOException {
    return Files.createDirectory(tempDir.resolve("base"));
  }

  private Path newFile(Path dir, String name) throws IOException {
    return Files.writeString(dir.resolve(name), "1");
  }

  private static void assertEscapes(String raw, Path base) {
    assertThatThrownBy(() -> PathValidator.validate(raw, base, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(CONTEXT + " must be located within");
  }

  @Test
  void shouldRejectNullPath() {
    assertThatThrownBy(() -> PathValidator.validate(null, null, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CONTEXT + " must not be null or blank");
  }

  @Test
  void shouldRejectWhitespaceOnlyPath() {
    assertThatThrownBy(() -> PathValidator.validate(" \t ", null, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CONTEXT + " must not be null or blank");
  }

  @Test
  void shouldAcceptExistingFileInsideBase() throws IOException {
    Path base = newBase();
    Path file = newFile(base, "seed.txt");

    assertThat(PathValidator.validate(file.toString(), base, CONTEXT)).isEqualTo(file.toRealPath());
  }

  @Test
  void shouldRejectDotDotEscapeToExistingFileOutsideBase() throws IOException {
    Path base = newBase();
    Path outside = newFile(tempDir, "secret.txt");

    assertEscapes(base + "/../" + outside.getFileName(), base);
  }

  @Test
  void shouldRejectDotDotInTheMiddleThatEscapesBase() throws IOException {
    Path base = newBase();
    Files.createDirectory(base.resolve("a"));
    newFile(tempDir, "x");

    assertEscapes(base + "/a/../../x", base);
  }

  @Test
  void shouldRejectDotDotEscapeWhenTargetDoesNotExist() throws IOException {
    Path base = newBase();

    assertEscapes(base + "/../nonexistent-outside.txt", base);
  }

  @Test
  void shouldAcceptDotDotThatStaysInsideBase() throws IOException {
    Path base = newBase();
    Files.createDirectory(base.resolve("a"));
    Path file = newFile(base, "x");

    assertThat(PathValidator.validate(base + "/a/../x", base, CONTEXT))
        .isEqualTo(file.toRealPath());
  }

  @Test
  void shouldRejectAbsolutePathOutsideBase(@TempDir Path other) throws IOException {
    Path base = newBase();
    Path file = newFile(other, "seed.txt");

    assertEscapes(file.toString(), base);
  }

  @Test
  void shouldRejectSiblingDirectorySharingBasePrefix() throws IOException {
    Path base = newBase();
    Path evil = Files.createDirectory(tempDir.resolve("base-evil"));
    Path file = newFile(evil, "x");

    assertEscapes(file.toString(), base);
  }

  @Test
  void shouldRejectNonexistentSiblingSharingBasePrefix() throws IOException {
    Path base = newBase();

    assertEscapes(tempDir.resolve("base-evil").resolve("x").toString(), base);
  }

  @Test
  void shouldRejectBaseDirectoryItselfAsNotARegularFile() throws IOException {
    Path base = newBase();
    String basePath = base.toString();

    assertThatThrownBy(() -> PathValidator.validate(basePath, base, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(CONTEXT + " is not a regular file");
  }

  @Test
  void shouldRejectSubdirectoryInsideBaseAsNotARegularFile() throws IOException {
    Path base = newBase();
    Path sub = Files.createDirectory(base.resolve("sub"));
    String subPath = sub.toString();

    assertThatThrownBy(() -> PathValidator.validate(subPath, base, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not a regular file");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectFileSymlinkInsideBasePointingOutside() throws IOException {
    Path base = newBase();
    Path outside = newFile(tempDir, "secret.txt");
    Path link = base.resolve("link.txt");
    assumeTrue(trySymlink(link, outside), "filesystem refuses symlinks");

    assertEscapes(link.toString(), base);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectFileReachedThroughDirectorySymlinkPointingOutside() throws IOException {
    Path base = newBase();
    Path outsideDir = Files.createDirectory(tempDir.resolve("outside"));
    newFile(outsideDir, "secret.txt");
    Path link = base.resolve("linkdir");
    assumeTrue(trySymlink(link, outsideDir), "filesystem refuses symlinks");

    assertEscapes(link + "/secret.txt", base);
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldAcceptSymlinkInsideBasePointingToAnotherFileInsideBase() throws IOException {
    Path base = newBase();
    Path target = newFile(base, "real.txt");
    Path link = base.resolve("link.txt");
    assumeTrue(trySymlink(link, target), "filesystem refuses symlinks");

    assertThat(PathValidator.validate(link.toString(), base, CONTEXT))
        .isEqualTo(target.toRealPath());
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldAcceptFileInsideBaseWhenBaseItselfIsASymlink() throws IOException {
    Path realBase = newBase();
    Path file = newFile(realBase, "seed.txt");
    Path baseLink = tempDir.resolve("base-link");
    assumeTrue(trySymlink(baseLink, realBase), "filesystem refuses symlinks");

    assertThat(PathValidator.validate(baseLink.resolve("seed.txt").toString(), baseLink, CONTEXT))
        .isEqualTo(file.toRealPath());
  }

  @Test
  void shouldTreatUrlEncodedDotsAsLiteralFileNameNotTraversal() throws IOException {
    Path base = newBase();
    String raw = base + "/%2e%2e/secret.txt";

    Path resolved = PathValidator.validate(raw, base, CONTEXT);

    assertThat(resolved).isEqualTo(base.toRealPath().resolve("%2e%2e").resolve("secret.txt"));
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldTreatBackslashAsLiteralCharacterOnPosixSoMixedSeparatorsCannotEscape()
      throws IOException {
    Path base = newBase();
    String raw = base + "/sub\\..\\..\\x";

    Path resolved = PathValidator.validate(raw, base, CONTEXT);

    assertThat(resolved).hasParentRaw(base.toRealPath());
    assertThat(resolved.getFileName()).hasToString("sub\\..\\..\\x");
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  void shouldRejectDifferentlyCasedBaseOnCaseSensitiveFileSystem() throws IOException {
    Path base = Files.createDirectory(tempDir.resolve("Base"));
    assumeTrue(!Files.exists(tempDir.resolve("base")), "case-insensitive filesystem");

    assertEscapes(tempDir.resolve("base").resolve("f.txt").toString(), base);
  }

  @Test
  void shouldCollapseDotDotWithoutContainmentCheckWhenBaseIsNull() throws IOException {
    Path dir = Files.createDirectory(tempDir.resolve("d"));
    Path file = newFile(tempDir, "f.txt");

    assertThat(PathValidator.validate(dir + "/../f.txt", null, CONTEXT))
        .isEqualTo(file.toRealPath());
  }

  @Test
  void shouldReturnNormalizedPathWhenFileDoesNotExist() throws IOException {
    Path base = newBase();

    Path resolved = PathValidator.validate(base + "/a/../new.txt", base, CONTEXT);

    assertThat(resolved).isEqualTo(base.toRealPath().resolve("new.txt"));
  }

  private static boolean trySymlink(Path link, Path target) {
    try {
      Files.createSymbolicLink(link, target);
      return true;
    } catch (IOException | UnsupportedOperationException | SecurityException e) {
      return false;
    }
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
