# TASK-008: Generators Module - Composite Generators

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-007 (Primitive Generators)  
**Human Supervision**: LOW (object/array composition logic)

---

## Objective

Implement generators for composite data types: objects (nested structures) and arrays (variable-length collections).

---

## Background

Composite generators build complex data structures:
- **object[address]**: Generate nested address structure
- **array[int[1..10], 5..20]**: Generate array of 5-20 integers
- **array[object[line_item], 1..50]**: Generate array of 1-50 nested objects

---

## Implementation Details

### Step 1: Create StructureRegistry

**File**: `generators/src/main/java/com/datagenerator/generators/StructureRegistry.java`

```java
package com.datagenerator.generators;

import com.datagenerator.schema.DataStructureParser;
import com.datagenerator.schema.model.DataStructure;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for loading and caching data structures.
 */
@Slf4j
public class StructureRegistry {
    
    private final String structuresPath;
    private final DataStructureParser parser;
    private final Map<String, DataStructure> cache;
    
    public StructureRegistry(String structuresPath) {
        this.structuresPath = structuresPath;
        this.parser = new DataStructureParser();
        this.cache = new ConcurrentHashMap<>();
    }
    
    /**
     * Get a structure by name, loading from file if not cached.
     */
    public DataStructure getStructure(String name) {
        return cache.computeIfAbsent(name, this::loadStructure);
    }
    
    /**
     * Manually register a structure (for testing).
     */
    public void register(DataStructure structure) {
        cache.put(structure.getName(), structure);
    }
    
    private DataStructure loadStructure(String name) {
        try {
            String filename = name.endsWith(".yaml") ? name : name + ".yaml";
            Path path = Paths.get(structuresPath, filename);
            
            if (!Files.exists(path)) {
                throw new GeneratorException("Structure file not found: " + path);
            }
            
            String yaml = Files.readString(path);
            DataStructure structure = parser.parse(yaml);
            
            log.debug("Loaded structure: {}", name);
            return structure;
            
        } catch (IOException e) {
            throw new GeneratorException("Failed to load structure: " + name, e);
        }
    }
}
```

---

### Step 2: Create ObjectGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/ObjectGenerator.java`

```java
package com.datagenerator.generators;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import lombok.RequiredArgsConstructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates nested object structures.
 */
@RequiredArgsConstructor
public class ObjectGenerator implements DataTypeGenerator<Map<String, Object>> {
    
    private final String structureName;
    private final StructureRegistry registry;
    private final DataGeneratorFactory factory;
    
    @Override
    public Map<String, Object> generate(Random random) {
        DataStructure structure = registry.getStructure(structureName);
        
        Map<String, Object> record = new HashMap<>();
        
        for (FieldDefinition field : structure.getFields()) {
            DataTypeGenerator<?> generator = factory.createGenerator(
                field.getDatatype(), 
                structure.getGeolocationOrDefault()
            );
            
            Object value = generator.generate(random);
            
            // Use alias if specified, otherwise field name
            String outputName = field.getOutputName();
            record.put(outputName, value);
        }
        
        return record;
    }
}
```

---

### Step 3: Create ArrayGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/ArrayGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates arrays of variable length with typed elements.
 */
@RequiredArgsConstructor
public class ArrayGenerator implements DataTypeGenerator<List<Object>> {
    
    private final DataTypeGenerator<?> elementGenerator;
    private final int minLength;
    private final int maxLength;
    
    @Override
    public List<Object> generate(Random random) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        
        List<Object> array = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            array.add(elementGenerator.generate(random));
        }
        
        return array;
    }
}
```

---

### Step 4: Create DataGeneratorFactory

**File**: `generators/src/main/java/com/datagenerator/generators/DataGeneratorFactory.java`

```java
package com.datagenerator.generators;

import com.datagenerator.core.type.TypeParser;
import com.datagenerator.core.type.DataType;
import com.datagenerator.core.type.PrimitiveType;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.core.type.ArrayType;
import com.datagenerator.core.type.EnumType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * Factory for creating generators based on type specifications.
 */
public class DataGeneratorFactory {
    
    private final TypeParser typeParser;
    private StructureRegistry registry;
    
    public DataGeneratorFactory() {
        this.typeParser = new TypeParser();
    }
    
    public void setRegistry(StructureRegistry registry) {
        this.registry = registry;
    }
    
    public DataTypeGenerator<?> createGenerator(String typeSpec, String geolocation) {
        DataType type = typeParser.parse(typeSpec);
        return createGenerator(type, geolocation);
    }
    
    private DataTypeGenerator<?> createGenerator(DataType type, String geolocation) {
        return switch (type) {
            case PrimitiveType pt -> createPrimitiveGenerator(pt);
            case ObjectType ot -> new ObjectGenerator(ot.getStructureName(), registry, this);
            case ArrayType at -> new ArrayGenerator(
                createGenerator(at.getElementType(), geolocation),
                at.getMinLength(),
                at.getMaxLength()
            );
            case EnumType et -> new EnumGenerator(et.getValues());
            default -> throw new GeneratorException("Unsupported type: " + type);
        };
    }
    
    @SuppressWarnings("unchecked")
    private DataTypeGenerator<?> createPrimitiveGenerator(PrimitiveType type) {
        return switch (type.getKind()) {
            case CHAR -> {
                var range = (PrimitiveType.IntRange) type.getRange();
                yield new CharGenerator(range.getMin(), range.getMax());
            }
            case INT -> {
                var range = (PrimitiveType.IntRange) type.getRange();
                yield new IntGenerator(range.getMin(), range.getMax());
            }
            case DECIMAL -> {
                var range = (PrimitiveType.DecimalRange) type.getRange();
                yield new DecimalGenerator(range.getMin(), range.getMax());
            }
            case BOOLEAN -> new BooleanGenerator();
            case DATE -> {
                var range = (PrimitiveType.DateRange) type.getRange();
                yield new DateGenerator(range.getMin(), range.getMax());
            }
            case TIMESTAMP -> {
                var range = (PrimitiveType.TimestampRange) type.getRange();
                yield new TimestampGenerator(range.getMin(), range.getMax());
            }
            default -> throw new GeneratorException("Unsupported primitive: " + type.getKind());
        };
    }
}
```

---

### Step 5: Create GeneratorException

**File**: `generators/src/main/java/com/datagenerator/generators/GeneratorException.java`

```java
package com.datagenerator.generators;

/**
 * Exception thrown when generation fails.
 */
public class GeneratorException extends RuntimeException {
    
    public GeneratorException(String message) {
        super(message);
    }
    
    public GeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 6: Write Unit Tests

**File**: `generators/src/test/java/com/datagenerator/generators/CompositeGeneratorsTest.java`

```java
package com.datagenerator.generators;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class CompositeGeneratorsTest {
    
    private final Random random = new Random(12345);
    
    @Test
    void objectGeneratorShouldGenerateNestedStructure() {
        // Create simple structure
        DataStructure structure = new DataStructure(
            "test",
            "en-US",
            List.of(
                new FieldDefinition("name", "char[5..10]", null),
                new FieldDefinition("age", "int[18..65]", null)
            )
        );
        
        StructureRegistry registry = new StructureRegistry("config/structures");
        registry.register(structure);
        
        DataGeneratorFactory factory = new DataGeneratorFactory();
        factory.setRegistry(registry);
        
        ObjectGenerator generator = new ObjectGenerator("test", registry, factory);
        
        Map<String, Object> record = generator.generate(random);
        
        assertThat(record).containsKeys("name", "age");
        assertThat(record.get("name")).isInstanceOf(String.class);
        assertThat(record.get("age")).isInstanceOf(Integer.class);
    }
    
    @Test
    void arrayGeneratorShouldGenerateVariableLengthArray() {
        IntGenerator elementGenerator = new IntGenerator(1, 100);
        ArrayGenerator generator = new ArrayGenerator(elementGenerator, 5, 10);
        
        List<Object> array = generator.generate(random);
        
        assertThat(array).hasSizeBetween(5, 10);
        assertThat(array).allMatch(item -> item instanceof Integer);
    }
}
```

---

## Acceptance Criteria

- ✅ ObjectGenerator generates nested structures
- ✅ ArrayGenerator generates variable-length arrays
- ✅ StructureRegistry loads and caches structures
- ✅ DataGeneratorFactory creates appropriate generators
- ✅ Field aliases are applied correctly
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :generators:test
```

---

**Completion Date**: [Mark when complete]
