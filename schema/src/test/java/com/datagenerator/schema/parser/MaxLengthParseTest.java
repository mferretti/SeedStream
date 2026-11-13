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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Parse-time contract of the {@code max_length} / {@code length_unit} field keys (#440). */
class MaxLengthParseTest {
  private final DataStructureParser parser = new DataStructureParser();

  @TempDir Path tempDir;

  private Path structure(String extraLines) throws IOException {
    return Files.writeString(
        tempDir.resolve("s.yaml"), "name: s\ndata:\n  city:\n    datatype: city\n" + extraLines);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "    max_length: 15\n",
        "    max_length: 1\n",
        "    max_length: 15\n    length_unit: chars\n",
        "    max_length: 15\n    length_unit: bytes\n",
        "    max_length: 15\n    nullable: 0.2\n    alias: c\n"
      })
  void shouldAcceptValidCapKeys(String lines) throws IOException {
    Path file = structure(lines);

    assertThatCode(() -> parser.parse(file)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1.5", "\"abc\"", "true", "[1]"})
  void shouldRejectInvalidMaxLengthMentioningTheKey(String value) throws IOException {
    Path file = structure("    max_length: " + value + "\n");

    assertThatThrownBy(() -> parser.parse(file))
        .hasMessageContaining("max_length")
        .hasMessageNotContaining("Unrecognized");
  }

  @ParameterizedTest
  @ValueSource(strings = {"bits", "BYTES", "\"\"", "3", "true"})
  void shouldRejectUnknownLengthUnitMentioningTheKey(String unit) throws IOException {
    Path file = structure("    max_length: 5\n    length_unit: " + unit + "\n");

    assertThatThrownBy(() -> parser.parse(file))
        .hasMessageContaining("length_unit")
        .hasMessageNotContaining("Unrecognized");
  }

  @Test
  void shouldRejectLengthUnitWithoutMaxLength() throws IOException {
    Path file = structure("    length_unit: bytes\n");

    assertThatThrownBy(() -> parser.parse(file))
        .hasMessageContaining("length_unit")
        .hasMessageContaining("max_length")
        .hasMessageNotContaining("Unrecognized");
  }
}
