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

package com.datagenerator.generators.composite;

import static org.assertj.core.api.Assertions.*;

import com.datagenerator.core.structure.StructureLoader;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.EnumType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.ParentReferenceType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObjectGeneratorTest {
  private static final String F_STATUS = "status";
  private static final String V_ACTIVE = "ACTIVE";
  private static final String F_VALUE = "value";
  private static final String F_NESTED = "nested";
  private static final String F_ACTIVE = "active";
  private static final String STRUCT_ENTITY = "entity";
  private static final String STRUCT_ADDRESS = "address";

  private MockStructureLoader loader;
  private ObjectGenerator generator;
  private DataGeneratorFactory factory;

  @BeforeEach
  void setUp() {
    loader = new MockStructureLoader();
    StructureRegistry registry = new StructureRegistry(loader);
    Path structuresPath = Paths.get("test-structures");
    generator = new ObjectGenerator(registry, structuresPath);
    factory = new DataGeneratorFactory(registry, structuresPath);
  }

  /** Helper to generate with context (reduces boilerplate). */
  private Object generateWithContext(ObjectType objectType, Random random) {
    try (var ctx = GeneratorContext.enter(factory, null)) {
      return generator.generate(random, objectType);
    }
  }

  @Test
  void shouldSupportObjectType() {
    ObjectType objectType = new ObjectType("user");
    assertThat(generator.supports(objectType)).isTrue();
  }

  @Test
  void shouldNotSupportOtherTypes() {
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "10"))).isFalse();
    assertThat(generator.supports(new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"))).isFalse();
    assertThat(generator.supports(new EnumType(java.util.List.of("A", "B")))).isFalse();
  }

  @Test
  void shouldGenerateSimpleObjectWithPrimitiveFields() {
    Map<String, DataType> userFields = new LinkedHashMap<>();
    userFields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"));
    userFields.put("age", new PrimitiveType(PrimitiveType.Kind.INT, "18", "65"));
    loader.addStructure("user", userFields);

    ObjectType objectType = new ObjectType("user");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).containsOnlyKeys("name", "age");
    assertThat(result.get("name")).isInstanceOf(String.class);
    assertThat(result.get("age")).isInstanceOf(Integer.class);
    assertThat((String) result.get("name")).hasSizeBetween(3, 10);
    assertThat((Integer) result.get("age")).isBetween(18, 65);
  }

  @Test
  void shouldGenerateDeterministicObjectsWithSameSeed() {
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "1000"));
    fields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "15"));
    fields.put(F_ACTIVE, new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));
    loader.addStructure(STRUCT_ENTITY, fields);

    ObjectType objectType = new ObjectType(STRUCT_ENTITY);

    @SuppressWarnings("unchecked")
    Map<String, Object> obj1 =
        (Map<String, Object>) generateWithContext(objectType, new Random(999));
    @SuppressWarnings("unchecked")
    Map<String, Object> obj2 =
        (Map<String, Object>) generateWithContext(objectType, new Random(999));

    assertThat(obj1).isEqualTo(obj2);
  }

  @Test
  void shouldGenerateNestedObjects() {
    Map<String, DataType> addressFields = new LinkedHashMap<>();
    addressFields.put("city", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "20"));
    addressFields.put("zip", new PrimitiveType(PrimitiveType.Kind.INT, "10000", "99999"));
    loader.addStructure(STRUCT_ADDRESS, addressFields);

    Map<String, DataType> userFields = new LinkedHashMap<>();
    userFields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"));
    userFields.put(STRUCT_ADDRESS, new ObjectType(STRUCT_ADDRESS));
    loader.addStructure("user", userFields);

    ObjectType objectType = new ObjectType("user");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).containsOnlyKeys("name", STRUCT_ADDRESS);
    assertThat(result.get(STRUCT_ADDRESS)).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> addressResult = (Map<String, Object>) result.get(STRUCT_ADDRESS);
    assertThat(addressResult).containsOnlyKeys("city", "zip");
    assertThat(addressResult.get("city")).isInstanceOf(String.class);
    assertThat(addressResult.get("zip")).isInstanceOf(Integer.class);
  }

  @Test
  void shouldGenerateObjectWithArrayFields() {
    Map<String, DataType> itemFields = new LinkedHashMap<>();
    itemFields.put("price", new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"));
    loader.addStructure("item", itemFields);

    Map<String, DataType> orderFields = new LinkedHashMap<>();
    orderFields.put("items", new ArrayType(new ObjectType("item"), 1, 5));
    loader.addStructure("order", orderFields);

    ObjectType objectType = new ObjectType("order");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).containsOnlyKeys("items");
    assertThat(result.get("items")).isInstanceOf(java.util.List.class);

    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> items =
        (java.util.List<Map<String, Object>>) result.get("items");
    assertThat(items).hasSizeBetween(1, 5).allMatch(item -> item.containsKey("price"));
  }

  @Test
  void shouldGenerateObjectWithEnumField() {
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put(F_STATUS, new EnumType(java.util.List.of(V_ACTIVE, "INACTIVE", "PENDING")));
    fields.put("code", new PrimitiveType(PrimitiveType.Kind.INT, "100", "999"));
    loader.addStructure(STRUCT_ENTITY, fields);

    ObjectType objectType = new ObjectType(STRUCT_ENTITY);
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).containsOnlyKeys(F_STATUS, "code");
    assertThat(result.get(F_STATUS)).isIn(V_ACTIVE, "INACTIVE", "PENDING");
  }

  @Test
  void shouldPreserveFieldOrder() {
    // LinkedHashMap should preserve insertion order
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("field1", new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
    fields.put("field2", new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
    fields.put("field3", new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
    loader.addStructure("ordered", fields);

    ObjectType objectType = new ObjectType("ordered");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result.keySet()).containsExactly("field1", "field2", "field3");
  }

  @Test
  void shouldGenerateDeeplyNestedObjects() {
    // level3: {value: int[1..10]}
    Map<String, DataType> level3 = new LinkedHashMap<>();
    level3.put(F_VALUE, new PrimitiveType(PrimitiveType.Kind.INT, "1", "10"));
    loader.addStructure("level3", level3);

    // level2: {nested: object[level3]}
    Map<String, DataType> level2 = new LinkedHashMap<>();
    level2.put(F_NESTED, new ObjectType("level3"));
    loader.addStructure("level2", level2);

    // level1: {nested: object[level2]}
    Map<String, DataType> level1 = new LinkedHashMap<>();
    level1.put(F_NESTED, new ObjectType("level2"));
    loader.addStructure("level1", level1);

    ObjectType objectType = new ObjectType("level1");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).containsOnlyKeys(F_NESTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> level2Result = (Map<String, Object>) result.get(F_NESTED);
    assertThat(level2Result).containsOnlyKeys(F_NESTED);
    @SuppressWarnings("unchecked")
    Map<String, Object> level3Result = (Map<String, Object>) level2Result.get(F_NESTED);
    assertThat(level3Result).containsOnlyKeys(F_VALUE);
    assertThat(level3Result.get(F_VALUE)).isInstanceOf(Integer.class);
  }

  @Test
  void shouldThrowExceptionForWrongType() {
    Random random = new Random(42);

    var dummyType = new ObjectType("dummy");
    assertThatThrownBy(() -> generateWithContext(dummyType, random))
        .isInstanceOf(RuntimeException.class); // Structure not found
  }

  @Test
  void shouldCacheLoadedStructures() {
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"));
    loader.addStructure("cached", fields);

    ObjectType objectType = new ObjectType("cached");
    Random random = new Random(42);

    // Generate twice
    try (var ctx = GeneratorContext.enter(factory, null)) {
      generator.generate(random, objectType);
      generator.generate(random, objectType);
    }

    // Should only load once (cached)
    assertThat(loader.getLoadCount("cached")).isEqualTo(1);
  }

  @Test
  void shouldGenerateComplexObjectWithAllTypes() {
    // Complex structure with all supported field types
    Map<String, DataType> fields = new LinkedHashMap<>();
    fields.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "1000"));
    fields.put("name", new PrimitiveType(PrimitiveType.Kind.CHAR, "5", "20"));
    fields.put("balance", new PrimitiveType(PrimitiveType.Kind.DECIMAL, "0.0", "1000.0"));
    fields.put(F_ACTIVE, new PrimitiveType(PrimitiveType.Kind.BOOLEAN, null, null));
    fields.put("created", new PrimitiveType(PrimitiveType.Kind.DATE, "2020-01-01", "2025-12-31"));
    fields.put(
        "updated",
        new PrimitiveType(
            PrimitiveType.Kind.TIMESTAMP, "2020-01-01T00:00:00Z", "2025-12-31T23:59:59Z"));
    fields.put(F_STATUS, new EnumType(java.util.List.of("NEW", V_ACTIVE, "CLOSED")));
    fields.put("tags", new ArrayType(new PrimitiveType(PrimitiveType.Kind.CHAR, "3", "10"), 0, 5));
    loader.addStructure("complex", fields);

    ObjectType objectType = new ObjectType("complex");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    assertThat(result).hasSize(8);
    assertThat(result.get("id")).isInstanceOf(Integer.class);
    assertThat(result.get("name")).isInstanceOf(String.class);
    assertThat(result.get("balance")).isInstanceOf(java.math.BigDecimal.class);
    assertThat(result.get(F_ACTIVE)).isInstanceOf(Boolean.class);
    assertThat(result.get("created")).isInstanceOf(java.time.LocalDate.class);
    assertThat(result.get("updated")).isInstanceOf(java.time.Instant.class);
    assertThat(result.get(F_STATUS)).isIn("NEW", V_ACTIVE, "CLOSED");
    assertThat(result.get("tags")).isInstanceOf(java.util.List.class);
  }

  @Test
  void shouldPropagateScalarFieldsToNestedParentRefGenerators() {
    Map<String, DataType> bookFields = new LinkedHashMap<>();
    bookFields.put("author_id", new ParentReferenceType("id"));
    loader.addStructure("book", bookFields);

    Map<String, DataType> authorFields = new LinkedHashMap<>();
    authorFields.put("id", new PrimitiveType(PrimitiveType.Kind.INT, "1", "100"));
    authorFields.put("books", new ArrayType(new ObjectType("book"), 1, 1));
    loader.addStructure("author", authorFields);

    ObjectType objectType = new ObjectType("author");
    Random random = new Random(42);

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) generateWithContext(objectType, random);

    int authorId = (Integer) result.get("id");
    @SuppressWarnings("unchecked")
    java.util.List<Map<String, Object>> books =
        (java.util.List<Map<String, Object>>) result.get("books");
    assertThat(books).hasSize(1);
    assertThat(books.get(0)).containsEntry("author_id", authorId);
  }

  /** Mock StructureLoader for testing (avoids file I/O). */
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
