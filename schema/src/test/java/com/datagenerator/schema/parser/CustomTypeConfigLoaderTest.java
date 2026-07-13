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

import com.datagenerator.core.registry.DatafakerRegistry;
import com.datagenerator.schema.exception.SchemaParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomTypeConfigLoaderTest {

  @Test
  void shouldRegisterTypesAndAliases(@TempDir Path dir) throws IOException {
    Path config = dir.resolve("datafaker-types.yaml");
    Files.writeString(
        config,
        """
        types:
          loadertest_full_name: name.fullName
          loadertest_city: address.city
        aliases:
          loadertest_fullname: loadertest_full_name
        """);

    int registered = new CustomTypeConfigLoader().load(config);

    assertThat(registered).isEqualTo(2);
    assertThat(DatafakerRegistry.isRegistered("loadertest_full_name")).isTrue();
    assertThat(DatafakerRegistry.isRegistered("loadertest_city")).isTrue();
    assertThat(DatafakerRegistry.getCanonicalName("loadertest_fullname"))
        .isEqualTo("loadertest_full_name");
  }

  @Test
  void shouldRegisterRegexType(@TempDir Path dir) throws IOException {
    Path config = dir.resolve("regex-types.yaml");
    Files.writeString(
        config,
        """
        types:
          loadertest_msg_id: "regex:[A-Z0-9]{10,35}"
        """);

    // A regex: value routed to registerExpression would fail chain resolution, so a clean load
    // proves it was registered as a regexify pattern. Generation is covered in
    // DatafakerRegistryTest.
    int registered = new CustomTypeConfigLoader().load(config);

    assertThat(registered).isEqualTo(1);
    assertThat(DatafakerRegistry.isRegistered("loadertest_msg_id")).isTrue();
  }

  @Test
  void shouldFailOnInvalidRegexPattern(@TempDir Path dir) throws IOException {
    Path config = dir.resolve("bad-regex.yaml");
    Files.writeString(config, "types:\n  broken_regex: \"regex:[unterminated\"\n");
    CustomTypeConfigLoader loader = new CustomTypeConfigLoader();

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("broken_regex");
  }

  @Test
  void shouldFailOnInvalidExpression(@TempDir Path dir) throws IOException {
    Path config = dir.resolve("bad.yaml");
    Files.writeString(config, "types:\n  broken: no.suchMethodHere\n");
    CustomTypeConfigLoader loader = new CustomTypeConfigLoader();

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("broken");
  }
}
