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

package com.datagenerator.core.structure;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.exception.CircularReferenceException;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.PrimitiveType;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructureRegistryTest {
  private StructureRegistry registry;
  private MockStructureLoader loader;

  @BeforeEach
  void setUp() {
    loader = new MockStructureLoader();
    registry = new StructureRegistry(loader);
  }

  @Test
  void shouldLoadSimpleStructure() {
    Map<String, DataType> companyFields = new HashMap<>();
    companyFields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "50"));
    loader.addStructure("company", companyFields);

    Map<String, DataType> loaded = registry.loadStructure("company", Path.of("config/structures"));

    assertThat(loaded).hasSize(1);
    assertThat(loaded.get("name")).isInstanceOf(PrimitiveType.class);
  }

  @Test
  void shouldCacheLoadedStructures() {
    Map<String, DataType> fields = new HashMap<>();
    fields.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "999"));
    loader.addStructure("user", fields);

    registry.loadStructure("user", Path.of("config/structures"));
    registry.loadStructure("user", Path.of("config/structures")); // Second load from cache

    assertThat(loader.getLoadCount("user")).isEqualTo(1); // Only loaded once
  }

  @Test
  void shouldDetectDirectCircularReference() {
    Map<String, DataType> selfRefFields = new HashMap<>();
    selfRefFields.put("next", new ObjectType("self_ref"));
    loader.addStructure("self_ref", selfRefFields);

    assertThatThrownBy(() -> registry.loadStructure("self_ref", Path.of("config/structures")))
        .isInstanceOf(CircularReferenceException.class)
        .hasMessageContaining("self_ref → self_ref");
  }

  @Test
  void shouldDetectIndirectCircularReference() {
    // A → B → A cycle
    Map<String, DataType> aFields = new HashMap<>();
    aFields.put("b_ref", new ObjectType("structure_b"));
    loader.addStructure("structure_a", aFields);

    Map<String, DataType> bFields = new HashMap<>();
    bFields.put("a_ref", new ObjectType("structure_a"));
    loader.addStructure("structure_b", bFields);

    assertThatThrownBy(() -> registry.loadStructure("structure_a", Path.of("config/structures")))
        .isInstanceOf(CircularReferenceException.class)
        .hasMessageContaining("Circular reference detected")
        .hasMessageContaining("structure_a")
        .hasMessageContaining("structure_b");
  }

  @Test
  void shouldDetectCircularReferenceInArrays() {
    Map<String, DataType> fields = new HashMap<>();
    fields.put("children", new ArrayType(new ObjectType("node"), 0, 10));
    loader.addStructure("node", fields);

    assertThatThrownBy(() -> registry.loadStructure("node", Path.of("config/structures")))
        .isInstanceOf(CircularReferenceException.class)
        .hasMessageContaining("node → node");
  }

  @Test
  void shouldAllowMultipleReferencesToSameStructure() {
    // Invoice references company twice (issuer, recipient) - should be OK
    Map<String, DataType> companyFields = new HashMap<>();
    companyFields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "50"));
    loader.addStructure("company", companyFields);

    Map<String, DataType> invoiceFields = new HashMap<>();
    invoiceFields.put("issuer", new ObjectType("company"));
    invoiceFields.put("recipient", new ObjectType("company"));
    loader.addStructure("invoice", invoiceFields);

    Map<String, DataType> loaded = registry.loadStructure("invoice", Path.of("config/structures"));

    assertThat(loaded).hasSize(2);
    assertThat(loader.getLoadCount("company")).isEqualTo(1); // Company loaded once, reused
  }

  @Test
  void shouldValidateNestedReferences() {
    Map<String, DataType> lineItemFields = new HashMap<>();
    lineItemFields.put("price", new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.01", "9999.99"));
    loader.addStructure("line_item", lineItemFields);

    Map<String, DataType> invoiceFields = new HashMap<>();
    invoiceFields.put("items", new ArrayType(new ObjectType("line_item"), 1, 20));
    loader.addStructure("invoice", invoiceFields);

    Map<String, DataType> loaded = registry.loadStructure("invoice", Path.of("config/structures"));

    registry.validateReferences(loaded, Path.of("config/structures"));
    // Should complete without exception
  }

  /** Mock loader for testing */
  static class MockStructureLoader implements StructureLoader {
    private final Map<String, Map<String, DataType>> structures = new HashMap<>();
    private final Map<String, Integer> loadCounts = new HashMap<>();

    void addStructure(String name, Map<String, DataType> fields) {
      structures.put(name, fields);
    }

    int getLoadCount(String name) {
      return loadCounts.getOrDefault(name, 0);
    }

    @Override
    public Map<String, DataType> load(
        String structureName, Path structuresPath, StructureRegistry registry) {
      loadCounts.merge(structureName, 1, Integer::sum);

      Map<String, DataType> fields = structures.get(structureName);
      if (fields == null) {
        throw new RuntimeException("Structure not found: " + structureName);
      }

      // Trigger nested loading for ObjectType fields (simulates real behavior)
      for (DataType type : fields.values()) {
        if (type instanceof ObjectType objectType) {
          registry.loadStructure(objectType.getStructureName(), structuresPath);
        } else if (type instanceof ArrayType arrayType
            && arrayType.getElementType() instanceof ObjectType objectType) {
          registry.loadStructure(objectType.getStructureName(), structuresPath);
        }
      }

      return fields;
    }
  }
}
