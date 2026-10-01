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

package com.datagenerator.schema.parser;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.datagenerator.schema.exception.SchemaParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the shared file-handling branches of AbstractYamlParser through both parsers. */
class AbstractYamlParserTest {
  @TempDir Path tempDir;

  @Test
  void shouldWrapIoExceptionWhenJobPathIsDirectory() throws Exception {
    Path dir = Files.createDirectory(tempDir.resolve("jobs.yaml"));
    assertThatThrownBy(() -> new JobConfigParser().parse(dir))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read job config file")
        .hasMessageContaining(dir.toString())
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldWrapIoExceptionWhenStructurePathIsDirectory() throws Exception {
    Path dir = Files.createDirectory(tempDir.resolve("structs.yaml"));
    assertThatThrownBy(() -> new DataStructureParser().parse(dir))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read data structure file")
        .hasMessageContaining(dir.toString())
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldNameDescriptionAndFullPathWhenJobFileMissing() {
    Path missing = tempDir.resolve("nested").resolve("nope.yaml");
    assertThatThrownBy(() -> new JobConfigParser().parse(missing))
        .isInstanceOf(SchemaParseException.class)
        .hasMessage("job config file not found: " + missing);
  }

  @Test
  void shouldNameDescriptionAndFullPathWhenStructureFileMissing() {
    Path missing = tempDir.resolve("nope.yaml");
    assertThatThrownBy(() -> new DataStructureParser().parse(missing))
        .isInstanceOf(SchemaParseException.class)
        .hasMessage("data structure file not found: " + missing);
  }

  @Test
  void shouldWrapIoExceptionWhenFileIsUnreadable() throws Exception {
    Path f = tempDir.resolve("locked.yaml");
    Files.writeString(f, "name: n\n");
    Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("---------"));
    assumeTrue(!Files.isReadable(f), "running as root: permissions not enforced");
    assertThatThrownBy(() -> new DataStructureParser().parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read data structure file")
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldFollowSymlinkToValidFile() throws Exception {
    Path real = tempDir.resolve("real.yaml");
    Files.writeString(real, "name: n\ndata:\n  a:\n    datatype: boolean\n");
    // The record name must match the file name it is referenced by: the link's name.
    Path link = Files.createSymbolicLink(tempDir.resolve("n.yaml"), real);
    assertThat(new DataStructureParser().parse(link).getName()).isEqualTo("n");
  }

  @Test
  void shouldReportMissingWhenSymlinkIsDangling() throws Exception {
    Path link = Files.createSymbolicLink(tempDir.resolve("dangling.yaml"), tempDir.resolve("gone"));
    assertThatThrownBy(() -> new DataStructureParser().parse(link))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("not found")
        .hasMessageContaining(link.toString());
  }
}
