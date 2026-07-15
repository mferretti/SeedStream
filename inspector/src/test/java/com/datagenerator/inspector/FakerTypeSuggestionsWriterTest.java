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

package com.datagenerator.inspector;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.inspector.FakerTypeSuggestionsWriter.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakerTypeSuggestionsWriterTest {

  private final FakerTypeSuggestionsWriter writer = new FakerTypeSuggestionsWriter();
  private final Map<String, String> suggestions = Map.of("sku", "regex:^[A-Z]{3}$");

  @Test
  void shouldWriteLoadableTypesYaml(@TempDir Path dir) throws IOException {
    FakerTypeSuggestionsWriter.Outcome outcome = writer.write(suggestions, dir, false, null);

    assertThat(outcome.result()).isEqualTo(Result.WRITTEN);
    assertThat(outcome.target()).isEqualTo(dir.resolve(FakerTypeSuggestionsWriter.FILE_NAME));
    String body = Files.readString(outcome.target());
    assertThat(body).contains("types:").contains("sku:").contains("regex:^[A-Z]{3}$");
  }

  @Test
  void shouldReturnNoneWhenNoSuggestions(@TempDir Path dir) {
    assertThat(writer.write(Map.of(), dir, false, null).result()).isEqualTo(Result.NONE);
  }

  @Test
  void shouldNotClobberExistingFileWithoutForce(@TempDir Path dir) throws IOException {
    Path target = dir.resolve(FakerTypeSuggestionsWriter.FILE_NAME);
    Files.writeString(target, "types:\n  hand_tuned: uuid\n");

    assertThat(writer.write(suggestions, dir, false, null).result())
        .isEqualTo(Result.SKIPPED_EXISTS);
    // untouched
    assertThat(Files.readString(target)).contains("hand_tuned");
  }

  @Test
  void shouldOverwriteExistingFileWithForce(@TempDir Path dir) throws IOException {
    Path target = dir.resolve(FakerTypeSuggestionsWriter.FILE_NAME);
    Files.writeString(target, "types:\n  hand_tuned: uuid\n");

    assertThat(writer.write(suggestions, dir, true, null).result()).isEqualTo(Result.WRITTEN);
    assertThat(Files.readString(target)).contains("sku").doesNotContain("hand_tuned");
  }

  @Test
  void shouldRefuseToOverwriteFakerTypesInput(@TempDir Path dir) throws IOException {
    // The run's --faker-types input happens to resolve to the companion path — must never clobber.
    Path input = dir.resolve(FakerTypeSuggestionsWriter.FILE_NAME);
    Files.writeString(input, "types:\n  mine: uuid\n");

    assertThat(writer.write(suggestions, dir, true, input).result())
        .isEqualTo(Result.SKIPPED_IS_INPUT);
    assertThat(Files.readString(input)).contains("mine");
  }
}
