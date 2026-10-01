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

import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DataStructureParserHardeningTest {
  private final DataStructureParser parser = new DataStructureParser();

  @TempDir Path tempDir;

  private Path write(String content) throws IOException {
    return Files.writeString(tempDir.resolve("s.yaml"), content);
  }

  private DataStructure parse(String content) throws IOException {
    return parser.parse(write(content));
  }

  // ---- alias / geolocation ----

  @Test
  void shouldMapAliasAndGeolocationToTheirOwnFields() throws Exception {
    DataStructure s =
        parse(
            """
            name: s
            geolocation: germany
            data:
              a:
                datatype: int[1..2]
                alias: x
              b:
                datatype: boolean
            """);
    assertThat(s.getGeolocation()).isEqualTo("germany");
    assertThat(s.getData().get("a").getAlias()).isEqualTo("x");
    assertThat(s.getData().get("a").getDatatype()).isEqualTo("int[1..2]");
    assertThat(s.getData().get("b").getAlias()).isNull();
  }

  @Test
  void shouldLeaveGeolocationNullWhenAbsent() throws Exception {
    assertThat(parse("name: s\ndata:\n  a:\n    datatype: boolean\n").getGeolocation()).isNull();
  }

  @Test
  void shouldKeepAliasEqualToAnotherFieldNameWithoutValidation() throws Exception {
    // documents: alias collisions are not checked at parse time
    DataStructure s =
        parse(
            """
            name: s
            data:
              a:
                datatype: boolean
                alias: b
              b:
                datatype: boolean
            """);
    assertThat(s.getData().get("a").getAlias()).isEqualTo("b");
  }

  // ---- missing / wrong shapes ----

  @Test
  void shouldNameFieldWhenDatatypeMissing() throws Exception {
    Path f = write("name: s\ndata:\n  good:\n    datatype: boolean\n  broken:\n    alias: z\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed")
        .hasMessageContaining("data[broken].datatype: must not be null")
        .hasMessageNotContaining("good");
  }

  @Test
  void shouldFailWhenFieldIsScalarInsteadOfMap() throws Exception {
    Path f = write("name: s\ndata:\n  a: int[1..2]\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  @Test
  void shouldFailWhenDataIsListInsteadOfMap() throws Exception {
    Path f = write("name: s\ndata:\n  - datatype: boolean\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read data structure")
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class)
        .cause()
        .hasMessageContaining("data");
  }

  @Test
  void shouldFailWhenDataIsMissing() throws Exception {
    Path f = write("name: s\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("data: must not be empty")
        .hasMessageNotContaining("name:");
  }

  @Test
  void shouldFailNamingOnlyNameWhenNameMissing() throws Exception {
    Path f = write("data:\n  a:\n    datatype: boolean\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("name: must not be null")
        .hasMessageNotContaining("data");
  }

  @Test
  void shouldRejectUnknownFieldOption() throws Exception {
    Path f = write("name: s\ndata:\n  a:\n    datatype: boolean\n    aliass: z\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("aliass");
  }

  @Test
  void shouldRejectUnknownTopLevelKey() throws Exception {
    Path f = write("name: s\ngeolocaton: it\ndata:\n  a:\n    datatype: boolean\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .cause()
        .hasMessageContaining("geolocaton");
  }

  @Test
  void shouldFailWhenFileIsEmpty() throws Exception {
    Path f = write("");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Failed to read data structure")
        .hasMessageContaining(f.toString())
        .cause()
        .hasMessageContaining("No content");
  }

  @Test
  void shouldFailWhenFileHasOnlyComments() throws Exception {
    Path f = write("# just a comment\n# another\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString());
  }

  @Test
  void shouldFailWhenTopLevelIsAList() throws Exception {
    Path f = write("- name: s\n");
    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining(f.toString())
        .hasCauseInstanceOf(JsonProcessingException.class);
  }

  // ---- duplicates / special names / order ----

  @Test
  void shouldRejectDuplicatedFieldKeyInsteadOfDroppingTheFirstDefinition() throws Exception {
    Path f =
        write(
            """
            name: s
            data:
              a:
                datatype: int[1..2]
              b:
                datatype: boolean
              a:
                datatype: char[3..4]
            """);

    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasStackTraceContaining("Duplicate field 'a'");
  }

  @Test
  void shouldRejectDuplicatedNameKey() throws Exception {
    Path f = write("name: s\nname: s\ndata:\n  a:\n    datatype: boolean\n");

    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("s.yaml")
        .hasStackTraceContaining("Duplicate field 'name'");
  }

  @Test
  void shouldPreserveSpecialFieldNamesExactlyAndInDeclarationOrder() throws Exception {
    DataStructure s =
        parse(
            String.join(
                "\n",
                "name: s",
                "data:",
                "  zeta:",
                "    datatype: boolean",
                "  a-b:",
                "    datatype: boolean",
                "  \"@type\":",
                "    datatype: boolean",
                "  'quoted key':",
                "    datatype: boolean",
                "  \"dotted.name\":",
                "    datatype: boolean",
                "  città:",
                "    datatype: boolean",
                "  \"日本\":",
                "    datatype: boolean",
                "  \"123\":",
                "    datatype: boolean",
                "  alpha:",
                "    datatype: boolean",
                ""));
    assertThat(s.getData().keySet())
        .containsExactly(
            "zeta", "a-b", "@type", "quoted key", "dotted.name", "città", "日本", "123", "alpha");
  }

  @Test
  void shouldKeepOrderForManyFieldsNotSortedAlphabetically() throws Exception {
    StringBuilder y = new StringBuilder("name: s\ndata:\n");
    for (int i = 30; i >= 1; i--) {
      y.append("  f").append(i).append(":\n    datatype: boolean\n");
    }
    DataStructure s = parse(y.toString());
    assertThat(s.getData().keySet().iterator().next()).isEqualTo("f30");
    assertThat(s.getData().keySet()).last().isEqualTo("f1");
    assertThat(s.getData()).hasSize(30);
  }

  @ParameterizedTest
  @ValueSource(strings = {"../../etc/passwd", "a/b", "Order", "line-item", "2nd"})
  void shouldRejectNameThatIsNotALowercaseIdentifier(String name) throws Exception {
    Path f = write("name: \"" + name + "\"\ndata:\n  a:\n    datatype: boolean\n");

    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("name:")
        .hasMessageContaining("lowercase identifier");
  }

  @Test
  void shouldRejectNameThatDiffersFromTheFileName() throws Exception {
    Path f = write("name: client\ndata:\n  a:\n    datatype: boolean\n");

    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("name: 'client' must match the file name ('s')");
  }

  @Test
  void shouldAcceptNameEqualToFileNameWithYmlExtension() throws Exception {
    Path f =
        Files.writeString(
            tempDir.resolve("orders_2.yml"),
            "name: orders_2\ndata:\n  a:\n    datatype: boolean\n");

    assertThat(parser.parse(f).getName()).isEqualTo("orders_2");
  }

  @Test
  void shouldRejectBlankName() throws Exception {
    Path f = write("name: \"\"\ndata:\n  a:\n    datatype: boolean\n");

    assertThatThrownBy(() -> parser.parse(f))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("name:");
  }

  @Test
  void shouldAcceptEmptyDatatypeStringWithoutValidation() throws Exception {
    // documents: datatype syntax is not checked by the schema module
    assertThat(parse("name: s\ndata:\n  a:\n    datatype: \"\"\n").getData().get("a").getDatatype())
        .isEmpty();
  }

  // ---- round trip ----

  @Test
  void shouldRoundTripEveryDocumentedOptionIntoModel() throws Exception {
    DataStructure s =
        parse(
            String.join(
                "\n",
                "# comment line",
                "name: s",
                "geolocation: italy",
                "data:",
                "  id:",
                "    datatype: serial[100]",
                "  email:",
                "    datatype: unique[mail, 1..1000]",
                "    alias: e_mail",
                "  qty:",
                "    datatype: int[1..999]",
                "  price:",
                "    datatype: decimal[0.0..100.0]",
                "  flag:",
                "    datatype: boolean",
                "  code:",
                "    datatype: char[3..15]",
                "  day:",
                "    datatype: date[2020-01-01..2025-12-31]",
                "  ts:",
                "    datatype: timestamp[now-30d..now]",
                "  status:",
                "    datatype: enum[A,B,C]",
                "    alias: state",
                "  customer:",
                "    datatype: ref[customer.id, 1..count]",
                "  addr:",
                "    datatype: object[address]",
                "  lines:",
                "    datatype: array[object[line_item], 1..50]",
                ""));
    assertThat(s.getName()).isEqualTo("s");
    assertThat(s.getGeolocation()).isEqualTo("italy");
    assertThat(s.getData()).hasSize(12);
    assertThat(s.getData().keySet())
        .containsExactly(
            "id",
            "email",
            "qty",
            "price",
            "flag",
            "code",
            "day",
            "ts",
            "status",
            "customer",
            "addr",
            "lines");
    assertField(s, "id", "serial[100]", null);
    assertField(s, "email", "unique[mail, 1..1000]", "e_mail");
    assertField(s, "qty", "int[1..999]", null);
    assertField(s, "price", "decimal[0.0..100.0]", null);
    assertField(s, "flag", "boolean", null);
    assertField(s, "code", "char[3..15]", null);
    assertField(s, "day", "date[2020-01-01..2025-12-31]", null);
    assertField(s, "ts", "timestamp[now-30d..now]", null);
    assertField(s, "status", "enum[A,B,C]", "state");
    assertField(s, "customer", "ref[customer.id, 1..count]", null);
    assertField(s, "addr", "object[address]", null);
    assertField(s, "lines", "array[object[line_item], 1..50]", null);
  }

  private static void assertField(DataStructure s, String field, String datatype, String alias) {
    FieldDefinition d = s.getData().get(field);
    assertThat(d).as(field).isNotNull();
    assertThat(d.getDatatype()).as(field + " datatype").isEqualTo(datatype);
    assertThat(d.getAlias()).as(field + " alias").isEqualTo(alias);
  }

  static Stream<Path> shippedRecordDefinitions() throws IOException {
    List<Path> files = new ArrayList<>();
    for (Path root : List.of(Path.of("../config/structures"), Path.of("../use-cases"))) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(
                p -> {
                  Path parent = p.getParent();
                  return parent != null && parent.endsWith("structures");
                })
            .filter(p -> p.toString().endsWith(".yaml"))
            .forEach(files::add);
      }
    }
    return files.stream().sorted();
  }

  @ParameterizedTest
  @MethodSource("shippedRecordDefinitions")
  void shouldParseEveryShippedRecordDefinitionWithMatchingName(Path file) {
    DataStructure structure = parser.parse(file);

    assertThat(file.getFileName()).hasToString(structure.getName() + ".yaml");
  }

  @Test
  void shouldFindShippedRecordDefinitions() throws IOException {
    assertThat(shippedRecordDefinitions()).hasSizeGreaterThan(20);
  }
}
