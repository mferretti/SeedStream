package com.datagenerator.generators.composite;

import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.generators.GeneratorException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
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

  public ObjectGenerator(StructureRegistry structureRegistry, Path structuresPath) {
    this.structureRegistry = structureRegistry;
    this.structuresPath = structuresPath;
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

    // Generate each field
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, DataType> entry : fields.entrySet()) {
      String fieldName = entry.getKey();
      DataType fieldType = entry.getValue();

      // Get generator for field type (recursive delegation via context)
      DataGenerator fieldGenerator = GeneratorContext.getFactory().create(fieldType);
      Object fieldValue = fieldGenerator.generate(random, fieldType);

      result.put(fieldName, fieldValue);
      log.trace("Generated field {}: {} = {}", structureName, fieldName, fieldValue);
    }

    return result;
  }
}
