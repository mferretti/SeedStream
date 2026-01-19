package com.datagenerator.schema.parser;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.schema.exception.SchemaParseException;
import com.datagenerator.schema.model.DataStructure;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataStructureParserTest {
  private DataStructureParser parser;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parser = new DataStructureParser();
  }

  @Test
  void shouldParseValidDataStructure() throws Exception {
    String yaml =
        """
            name: address
            geolocation: italy
            data:
              name:
                datatype: char[3..15]
                alias: "nome"
              city:
                datatype: char[3..40]
                alias: "citta"
            """;

    Path file = tempDir.resolve("address.yaml");
    Files.writeString(file, yaml);

    DataStructure structure = parser.parse(file);

    assertThat(structure.getName()).isEqualTo("address");
    assertThat(structure.getGeolocation()).isEqualTo("italy");
    assertThat(structure.getData()).hasSize(2);
    assertThat(structure.getData().get("name").getDatatype()).isEqualTo("char[3..15]");
    assertThat(structure.getData().get("name").getAlias()).isEqualTo("nome");
  }

  @Test
  void shouldFailWhenFileDoesNotExist() {
    Path nonExistent = tempDir.resolve("missing.yaml");

    assertThatThrownBy(() -> parser.parse(nonExistent))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void shouldFailWhenNameIsMissing() throws Exception {
    String yaml =
        """
            data:
              field1:
                datatype: char[1..10]
            """;

    Path file = tempDir.resolve("invalid.yaml");
    Files.writeString(file, yaml);

    assertThatThrownBy(() -> parser.parse(file))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed");
  }

  @Test
  void shouldFailWhenDataIsEmpty() throws Exception {
    String yaml =
        """
            name: test
            data: {}
            """;

    Path file = tempDir.resolve("empty.yaml");
    Files.writeString(file, yaml);

    assertThatThrownBy(() -> parser.parse(file))
        .isInstanceOf(SchemaParseException.class)
        .hasMessageContaining("Validation failed");
  }

  @Test
  void shouldParseWithoutGeolocation() throws Exception {
    String yaml =
        """
            name: simple
            data:
              id:
                datatype: int[1..100]
            """;

    Path file = tempDir.resolve("simple.yaml");
    Files.writeString(file, yaml);

    DataStructure structure = parser.parse(file);

    assertThat(structure.getName()).isEqualTo("simple");
    assertThat(structure.getGeolocation()).isNull();
    assertThat(structure.getData()).hasSize(1);
  }

  @Test
  void shouldParseFieldWithoutAlias() throws Exception {
    String yaml =
        """
            name: test
            data:
              field1:
                datatype: char[1..10]
            """;

    Path file = tempDir.resolve("no-alias.yaml");
    Files.writeString(file, yaml);

    DataStructure structure = parser.parse(file);

    assertThat(structure.getData().get("field1").getAlias()).isNull();
  }
}
