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

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.inspector.ddl.DdlInspector;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.parser.CustomTypeConfigLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end smoke: pull in Datafaker providers that are NOT built-in ({@code Beer}, {@code
 * Pokemon} from https://www.datafaker.net/documentation/providers/), inspect a DDL that uses them
 * by column name, and verify both the emitted datafaker types and the placement of review comments
 * in the written YAML.
 */
class CustomProviderSmokeTest {

  @Test
  void emitsCustomTypesWithCommentsInTheRightPlaces(@TempDir Path dir) throws IOException {
    // 1. Pull in providers absent from the built-in registry.
    assertThat(DatafakerRegistry.isRegistered("beer_style")).isFalse();
    assertThat(DatafakerRegistry.isRegistered("pokemon")).isFalse();

    Path typesConfig = dir.resolve("datafaker-types.yaml");
    Files.writeString(
        typesConfig,
        """
        types:
          beer_style: beer.style      # faker.beer().style()
          pokemon: pokemon.name       # faker.pokemon().name()
        """);
    assertThat(new CustomTypeConfigLoader().load(typesConfig)).isEqualTo(2);
    assertThat(DatafakerRegistry.isRegistered("beer_style")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("pokemon")).isTrue();

    // 2. DDL: two columns named after custom types, plus declared / default-range columns.
    Path sql = dir.resolve("beers.sql");
    Files.writeString(
        sql,
        """
        CREATE TABLE beers (
          id          BIGINT,
          beer_style  VARCHAR(40),
          pokemon     VARCHAR(60),
          abv         DECIMAL(4,2),
          label       VARCHAR(120)
        );
        """);
    Inspection inspection = new DdlInspector().inspect(sql);

    // 3. Correct datafaker types emitted (custom keys), others mapped normally.
    Map<String, String> types = datatypes(inspection, "beers");
    assertThat(types)
        .containsEntry("beer_style", "beer_style")
        .containsEntry("pokemon", "pokemon")
        .containsEntry("abv", "decimal[0.0..9999.99]")
        .containsEntry("id", "int[1..999999]")
        .containsEntry("label", "char[1..120]");

    // 4. Write the YAML and verify comment placement.
    DataStructure beers = structure(inspection, "beers");
    Map<String, String> comments = inspection.comments().getOrDefault("beers", Map.of());
    new StructureYamlWriter().write(beers, dir, true, comments);
    List<String> lines = Files.readAllLines(dir.resolve("beers.yaml"));

    // Comments ON the custom name-hint fields...
    assertThat(datatypeLine(lines, "beer_style"))
        .isEqualTo("    datatype: \"beer_style\"  # guessed from column name — verify");
    assertThat(datatypeLine(lines, "pokemon")).contains("# guessed from column name — verify");
    // ...and NOT on declared / default-range fields.
    assertThat(datatypeLine(lines, "abv")).doesNotContain("#");
    assertThat(datatypeLine(lines, "id")).doesNotContain("#");
    assertThat(datatypeLine(lines, "label")).doesNotContain("#");
  }

  private Map<String, String> datatypes(Inspection inspection, String name) {
    return structure(inspection, name).getData().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDatatype(), (a, b) -> a));
  }

  private DataStructure structure(Inspection inspection, String name) {
    return inspection.structures().stream()
        .filter(s -> s.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  /** Returns the {@code datatype:} line belonging to the given field key. */
  private String datatypeLine(List<String> lines, String field) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).strip().equals(field + ":")) {
        for (int j = i + 1; j < lines.size() && lines.get(j).startsWith("    "); j++) {
          if (lines.get(j).strip().startsWith("datatype:")) {
            return lines.get(j);
          }
        }
      }
    }
    return fail("no datatype line for field: " + field);
  }
}
