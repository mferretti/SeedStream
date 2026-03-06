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

import com.datagenerator.core.seed.SeedConfig;
import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.JobConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JobConfigParserTest {
  private JobConfigParser parser;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new JobConfigParser();
  }

  @Test
  void shouldParseJobWithEmbeddedSeed() throws Exception {
    String yaml =
        """
            source: address.yaml
            type: kafka
            seed:
              type: embedded
              value: 12345
            conf:
              bootstrap: localhost:9092
              topic: addresses
            """;

    Path file = tempDir.resolve("job.yaml");
    Files.writeString(file, yaml);

    JobConfig config = parser.parse(file);

    assertThat(config.getSource()).isEqualTo("address.yaml");
    assertThat(config.getType()).isEqualTo("kafka");
    assertThat(config.getSeed()).isInstanceOf(SeedConfig.EmbeddedSeed.class);
    assertThat(((SeedConfig.EmbeddedSeed) config.getSeed()).getValue()).isEqualTo(12345);
    assertThat(config.getConf().get("bootstrap").asText()).isEqualTo("localhost:9092");
  }

  @Test
  void shouldParseJobWithFileSeed() throws Exception {
    String yaml =
        """
            source: user.yaml
            type: file
            seed:
              type: file
              path: /secrets/seed.txt
            conf:
              path: output/users
            """;

    Path file = tempDir.resolve("job.yaml");
    Files.writeString(file, yaml);

    JobConfig config = parser.parse(file);

    assertThat(config.getSeed()).isInstanceOf(SeedConfig.FileSeed.class);
    assertThat(((SeedConfig.FileSeed) config.getSeed()).getPath()).isEqualTo("/secrets/seed.txt");
  }

  @Test
  void shouldParseJobWithEnvSeed() throws Exception {
    String yaml =
        """
            source: data.yaml
            type: database
            seed:
              type: env
              name: DATA_SEED
            conf:
              url: jdbc:postgresql://localhost/db
            """;

    Path file = tempDir.resolve("job.yaml");
    Files.writeString(file, yaml);

    JobConfig config = parser.parse(file);

    assertThat(config.getSeed()).isInstanceOf(SeedConfig.EnvSeed.class);
    assertThat(((SeedConfig.EnvSeed) config.getSeed()).getName()).isEqualTo("DATA_SEED");
  }

  @Test
  void shouldParseJobWithRemoteSeed() throws Exception {
    String yaml =
        """
            source: event.yaml
            type: kafka
            seed:
              type: remote
              url: https://seed-api.example.com/generate
              auth:
                type: bearer
                token: secret-token
            conf:
              bootstrap: localhost:9092
              topic: events
            """;

    Path file = tempDir.resolve("job.yaml");
    Files.writeString(file, yaml);

    JobConfig config = parser.parse(file);

    assertThat(config.getSeed()).isInstanceOf(SeedConfig.RemoteSeed.class);
    SeedConfig.RemoteSeed remoteSeed = (SeedConfig.RemoteSeed) config.getSeed();
    assertThat(remoteSeed.getUrl()).isEqualTo("https://seed-api.example.com/generate");
    assertThat(remoteSeed.getAuth().getType()).isEqualTo("bearer");
    assertThat(remoteSeed.getAuth().getToken()).isEqualTo("secret-token");
  }

  @Test
  void shouldParseJobWithoutSeed() throws Exception {
    String yaml =
        """
            source: simple.yaml
            type: file
            conf:
              path: output/data
            """;

    Path file = tempDir.resolve("job.yaml");
    Files.writeString(file, yaml);

    JobConfig config = parser.parse(file);

    assertThat(config.getSeed()).isNull();
  }

  @Test
  void shouldFailWhenSourceIsMissing() throws Exception {
    String yaml =
        """
            type: kafka
            conf:
              bootstrap: localhost:9092
            """;

    Path file = tempDir.resolve("invalid.yaml");
    Files.writeString(file, yaml);

    assertThatThrownBy(() -> parser.parse(file))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed");
  }

  @Test
  void shouldFailWhenTypeIsMissing() throws Exception {
    String yaml =
        """
            source: data.yaml
            conf:
              something: value
            """;

    Path file = tempDir.resolve("invalid.yaml");
    Files.writeString(file, yaml);

    assertThatThrownBy(() -> parser.parse(file))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed");
  }

  @Test
  void shouldFailWhenFileDoesNotExist() {
    Path nonExistent = tempDir.resolve("missing.yaml");

    assertThatThrownBy(() -> parser.parse(nonExistent))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("not found");
  }
}
