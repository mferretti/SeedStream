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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructureYamlWriterTest {

  private static final Map<String, String> NO_COMMENTS = Map.of();

  private final StructureYamlWriter writer = new StructureYamlWriter();

  private static DataStructure structure(String name) {
    return new DataStructure(name, null, Map.of("id", new FieldDefinition("int[1..100]", null)));
  }

  @Test
  void shouldWriteSafeName(@TempDir Path outputDir) {
    assertThat(writer.write(structure("customer"), outputDir, true, NO_COMMENTS)).isTrue();
    assertThat(Files.exists(outputDir.resolve("customer.yaml"))).isTrue();
  }

  @Test
  void shouldRejectPathTraversalName(@TempDir Path outputDir) {
    // I1 / CWE-22: a spec-derived name escaping the output dir must be refused.
    DataStructure malicious = structure("../../etc/cron.d/evil");
    assertThatThrownBy(() -> writer.write(malicious, outputDir, true, NO_COMMENTS))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("unsafe name");
    // Nothing escaped the output directory.
    assertThat(Files.exists(outputDir.getParent().resolve("etc"))).isFalse();
  }

  @Test
  void shouldRejectAbsoluteName(@TempDir Path outputDir) {
    DataStructure malicious = structure("/tmp/evil");
    assertThatThrownBy(() -> writer.write(malicious, outputDir, true, NO_COMMENTS))
        .isInstanceOf(InspectorException.class)
        .hasMessageContaining("unsafe name");
  }
}
