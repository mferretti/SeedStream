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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @Test
  void shouldKeepReviewCommentWhenFieldNameIsNotAWordToken(@TempDir Path outputDir)
      throws Exception {
    // OpenAPI / JSON Schema keep raw property names, so "postal-code" or "@type" reach the writer.
    DataStructure structure =
        new DataStructure(
            "customer",
            null,
            Map.of(
                "postal-code", new FieldDefinition("char[1..50]", null),
                "@type", new FieldDefinition("char[1..50]", null)));
    Map<String, String> comments = Map.of("postal-code", "REVIEW zip", "@type", "REVIEW type");

    writer.write(structure, outputDir, true, comments);

    String yaml = Files.readString(outputDir.resolve("customer.yaml"));
    assertThat(yaml).contains("# REVIEW zip").contains("# REVIEW type");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"id", "postal-code", "@type", "a:b", "it's", "città", "123start", "has space"})
  void shouldAttachCommentToDatatypeLineWhateverTheFieldKeySpelling(
      String fieldName, @TempDir Path outputDir) throws Exception {
    DataStructure structure =
        new DataStructure(
            "customer", null, Map.of(fieldName, new FieldDefinition("char[1..50]", null)));

    writer.write(structure, outputDir, true, Map.of(fieldName, "REVIEW me"));

    List<String> lines = Files.readAllLines(outputDir.resolve("customer.yaml"));
    assertThat(lines).filteredOn(l -> l.contains("# REVIEW me")).hasSize(1);
    assertThat(lines)
        .filteredOn(l -> l.contains("# REVIEW me"))
        .first()
        .asString()
        .startsWith("    datatype:");
  }

  @Test
  void shouldAnnotateOnlyCommentedFieldsWhenCommentedAndPlainFieldsAreMixed(@TempDir Path outputDir)
      throws Exception {
    Map<String, FieldDefinition> data = new LinkedHashMap<>();
    data.put("id", new FieldDefinition("int[1..10]", null));
    data.put("postal-code", new FieldDefinition("char[1..50]", null));
    data.put("name", new FieldDefinition("char[1..20]", null));
    data.put("@type", new FieldDefinition("char[1..30]", null));
    DataStructure structure = new DataStructure("customer", null, data);

    writer.write(
        structure, outputDir, true, Map.of("postal-code", "REVIEW zip", "@type", "REVIEW type"));

    List<String> lines = Files.readAllLines(outputDir.resolve("customer.yaml"));
    assertThat(lines)
        .filteredOn(l -> l.contains("#"))
        .containsExactly(
            "    datatype: \"char[1..50]\"  # REVIEW zip",
            "    datatype: \"char[1..30]\"  # REVIEW type");
  }

  @Test
  void shouldProduceValidYamlWithOriginalDatatypesWhenAnnotated(@TempDir Path outputDir)
      throws Exception {
    Map<String, FieldDefinition> data = new LinkedHashMap<>();
    data.put("postal-code", new FieldDefinition("char[1..50]", null));
    data.put("@type", new FieldDefinition("enum[A,B]", null));
    DataStructure structure = new DataStructure("customer", null, data);

    writer.write(structure, outputDir, true, Map.of("postal-code", "x", "@type", "y"));

    JsonNode parsed = new YAMLMapper().readTree(outputDir.resolve("customer.yaml").toFile());
    assertThat(parsed.at("/data/postal-code/datatype").asText()).isEqualTo("char[1..50]");
    assertThat(parsed.at("/data/@type/datatype").asText()).isEqualTo("enum[A,B]");
  }
}
