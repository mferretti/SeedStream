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

import com.datagenerator.core.record.FieldRecord;
import com.datagenerator.core.record.RecordSchema;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.util.LogUtils;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates nested objects by loading structure definitions and recursively generating field
 * values.
 *
 * <p><b>Algorithm:</b>
 *
 * <ol>
 *   <li>Load structure definition from {structuresPath}/{structureName}.yaml via StructureRegistry
 *   <li>For each field in structure, get appropriate generator via factory
 *   <li>Generate field value using field's DataType
 *   <li>Return Map&lt;String, Object&gt; with all field values
 * </ol>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * object[address] loads address.yaml:
 *   name: char[3..15]
 *   city: char[3..40]
 *   zip: int[10000..99999]
 * → {"name": "Milan", "city": "Rome", "zip": 12345}
 * </pre>
 *
 * <p><b>Dependencies:</b>
 *
 * <ul>
 *   <li>StructureRegistry for loading nested structure definitions
 *   <li>DataGeneratorFactory for recursive field generation
 * </ul>
 *
 * <p><b>Thread Safety:</b> This generator is stateless and thread-safe. StructureRegistry must be
 * thread-safe (uses concurrent cache).
 */
@Slf4j
public class ObjectGenerator implements DataGenerator {
  private final StructureRegistry structureRegistry;
  private final Path structuresPath;

  /**
   * Interned record layout per structure name. Built once (scalars-first, then nested — the order
   * {@link #generate} populates fields) and shared by every record of that structure so generation
   * allocates only a value array per record.
   */
  private final Map<String, RecordSchema> schemaCache = new ConcurrentHashMap<>();

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "StructureRegistry is a shared, thread-safe service object injected by the engine; "
              + "storing the reference is intentional")
  public ObjectGenerator(StructureRegistry registry, Path path) {
    this.structureRegistry = registry;
    this.structuresPath = path;
  }

  @Override
  public boolean supports(DataType type) {
    return type instanceof ObjectType;
  }

  @Override
  public Object generate(Random random, DataType type) {
    if (!(type instanceof ObjectType objectType)) {
      throw new GeneratorException("ObjectGenerator only supports ObjectType, got: " + type);
    }

    String structureName = objectType.getStructureName();
    log.debug("Generating object for structure: {}", structureName);

    // Load structure definition (cached by registry)
    Map<String, DataType> fields = structureRegistry.loadStructure(structureName, structuresPath);

    // Two-pass generation: scalars first so that all primitive fields are present in the partial
    // record before any nested (array/object) field is processed. This guarantees that
    // ref[parent.*] generators in child structures can access scalar fields (e.g. id) regardless
    // of the iteration order returned by the StructureLoader. The flyweight record's field order
    // matches this two-pass order, preserving the previous LinkedHashMap serialization order.
    RecordSchema schema = schemaCache.computeIfAbsent(structureName, k -> buildSchema(fields));
    Map<String, Object> result = new FieldRecord(schema);

    // Pass 1: generate all non-nested fields
    for (Map.Entry<String, DataType> entry : fields.entrySet()) {
      DataType fieldType = entry.getValue();
      if (fieldType instanceof ArrayType || fieldType instanceof ObjectType) continue;

      DataGenerator fieldGenerator = GeneratorContext.getFactory().create(fieldType);
      Object fieldValue = fieldGenerator.generate(random, fieldType);
      result.put(entry.getKey(), fieldValue);

      if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
        log.trace("Generated field {}: {} = {}", structureName, entry.getKey(), fieldValue);
      }
    }

    // Pass 2: generate nested fields, exposing the current partial record to child generators
    for (Map.Entry<String, DataType> entry : fields.entrySet()) {
      DataType fieldType = entry.getValue();
      if (!(fieldType instanceof ArrayType || fieldType instanceof ObjectType)) continue;

      GeneratorContext.pushParentRecord(result);
      Object fieldValue;
      try {
        DataGenerator fieldGenerator = GeneratorContext.getFactory().create(fieldType);
        fieldValue = fieldGenerator.generate(random, fieldType);
      } finally {
        GeneratorContext.popParentRecord();
      }
      result.put(entry.getKey(), fieldValue);

      if (log.isTraceEnabled() && LogUtils.shouldTrace()) {
        log.trace("Generated field {}: {} = {}", structureName, entry.getKey(), fieldValue);
      }
    }

    return result;
  }

  /**
   * Build the interned field layout for a structure in the same order {@link #generate} populates
   * it: all scalar fields (in declaration order) first, then all nested array/object fields. This
   * keeps the record's serialization order identical to the previous {@code LinkedHashMap}.
   *
   * @param fields structure field definitions
   * @return interned record schema
   */
  private static RecordSchema buildSchema(Map<String, DataType> fields) {
    List<String> ordered = new ArrayList<>(fields.size());
    for (Map.Entry<String, DataType> entry : fields.entrySet()) {
      DataType fieldType = entry.getValue();
      if (!(fieldType instanceof ArrayType || fieldType instanceof ObjectType)) {
        ordered.add(entry.getKey());
      }
    }
    for (Map.Entry<String, DataType> entry : fields.entrySet()) {
      DataType fieldType = entry.getValue();
      if (fieldType instanceof ArrayType || fieldType instanceof ObjectType) {
        ordered.add(entry.getKey());
      }
    }
    return new RecordSchema(ordered.toArray(new String[0]));
  }
}
