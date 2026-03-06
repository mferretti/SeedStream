# TASK-012: Generators Module - Reference Generator

**Status**: ⏸️ Not Started (Deferred to Phase 4)  
**Priority**: P2 (Medium)  
**Phase**: 4 - Advanced Features  
**Dependencies**: TASK-008 (Composite Generators)  
**Human Supervision**: MEDIUM (complex state management)

---

## Objective

Implement reference generator for foreign key relationships between generated records (`ref[user.id]`). Maintains a cache of previously generated records to enable referential integrity.

---

## Background

Real-world data often has relationships:
- `order.user_id` references `user.id`
- `line_item.order_id` references `order.id`
- `comment.author_id` references `user.id`

**Challenge**: References require generated records to be cached and queryable.

**Type Syntax**: `ref[structure.field]` where:
- `structure`: Target structure name (e.g., "user")
- `field`: Field name to reference (e.g., "id")

---

## Architecture Overview

```
Generation Flow:
1. Generate user records → cache user.id values
2. Generate order records → reference cached user.id values
3. Generate line_item records → reference cached order.id values
```

**Cache Strategy**:
- In-memory cache per structure
- Limited size (LRU eviction)
- Thread-safe (concurrent access)

---

## Implementation Details

### Step 1: Create ReferenceCache

**File**: `generators/src/main/java/com/datagenerator/generators/ReferenceCache.java`

```java
package com.datagenerator.generators;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe cache for storing generated values that can be referenced.
 */
@Slf4j
public class ReferenceCache {
    
    private final Map<String, List<Object>> cache;
    private final int maxSizePerKey;
    
    public ReferenceCache(int maxSizePerKey) {
        this.cache = new ConcurrentHashMap<>();
        this.maxSizePerKey = maxSizePerKey;
    }
    
    /**
     * Store a value that can be referenced.
     * 
     * @param key Cache key (structure.field)
     * @param value Value to cache
     */
    public void put(String key, Object value) {
        List<Object> values = cache.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        
        values.add(value);
        
        // LRU eviction if exceeds max size
        if (values.size() > maxSizePerKey) {
            values.remove(0);
        }
        
        log.trace("Cached value for key {}: {} (cache size: {})", key, value, values.size());
    }
    
    /**
     * Get a random value from the cache.
     * 
     * @param key Cache key (structure.field)
     * @param random Random instance for selection
     * @return Random value from cache
     * @throws GeneratorException if cache is empty
     */
    public Object getRandomValue(String key, Random random) {
        List<Object> values = cache.get(key);
        
        if (values == null || values.isEmpty()) {
            throw new GeneratorException("No cached values for reference: " + key + 
                " (generate referenced structure first)");
        }
        
        int index = random.nextInt(values.size());
        return values.get(index);
    }
    
    /**
     * Clear all cached values.
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Get cache statistics.
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new java.util.HashMap<>();
        cache.forEach((key, values) -> stats.put(key, values.size()));
        return stats;
    }
}
```

---

### Step 2: Create ReferenceGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/ReferenceGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.util.Random;

/**
 * Generates foreign key references to previously generated records.
 */
@RequiredArgsConstructor
public class ReferenceGenerator implements DataTypeGenerator<Object> {
    
    private final String targetStructure;
    private final String targetField;
    private final ReferenceCache cache;
    
    @Override
    public Object generate(Random random) {
        String cacheKey = targetStructure + "." + targetField;
        return cache.getRandomValue(cacheKey, random);
    }
}
```

---

### Step 3: Update ObjectGenerator to Cache Values

**File**: Update `generators/src/main/java/com/datagenerator/generators/ObjectGenerator.java`

Add cache support:

```java
@RequiredArgsConstructor
public class ObjectGenerator implements DataTypeGenerator<Map<String, Object>> {
    
    private final String structureName;
    private final StructureRegistry registry;
    private final DataGeneratorFactory factory;
    private final ReferenceCache cache; // Add this field
    
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
            String outputName = field.getOutputName();
            record.put(outputName, value);
            
            // Cache value for potential references
            if (cache != null) {
                String cacheKey = structureName + "." + field.getName();
                cache.put(cacheKey, value);
            }
        }
        
        return record;
    }
}
```

---

### Step 4: Update DataGeneratorFactory

Add ReferenceType handling:

```java
private DataTypeGenerator<?> createGenerator(DataType type, String geolocation) {
    return switch (type) {
        case PrimitiveType pt -> createPrimitiveGenerator(pt);
        case ObjectType ot -> new ObjectGenerator(ot.getStructureName(), registry, this, cache);
        case ArrayType at -> new ArrayGenerator(
            createGenerator(at.getElementType(), geolocation),
            at.getMinLength(),
            at.getMaxLength()
        );
        case EnumType et -> new EnumGenerator(et.getValues());
        case ReferenceType rt -> new ReferenceGenerator(
            rt.getTargetStructure(),
            rt.getTargetField(),
            cache
        );
        default -> throw new GeneratorException("Unsupported type: " + type);
    };
}
```

---

### Step 5: Write Unit Tests

**File**: `generators/src/test/java/com/datagenerator/generators/ReferenceGeneratorTest.java`

```java
package com.datagenerator.generators;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class ReferenceGeneratorTest {
    
    private final Random random = new Random(12345);
    
    @Test
    void shouldGenerateReferenceFromCache() {
        ReferenceCache cache = new ReferenceCache(100);
        cache.put("user.id", 1);
        cache.put("user.id", 2);
        cache.put("user.id", 3);
        
        ReferenceGenerator generator = new ReferenceGenerator("user", "id", cache);
        
        for (int i = 0; i < 10; i++) {
            Object value = generator.generate(random);
            assertThat(value).isIn(1, 2, 3);
        }
    }
    
    @Test
    void shouldThrowExceptionWhenCacheEmpty() {
        ReferenceCache cache = new ReferenceCache(100);
        ReferenceGenerator generator = new ReferenceGenerator("user", "id", cache);
        
        assertThatThrownBy(() -> generator.generate(random))
            .isInstanceOf(GeneratorException.class)
            .hasMessageContaining("No cached values");
    }
    
    @Test
    void shouldEvictOldValuesWhenCacheFull() {
        ReferenceCache cache = new ReferenceCache(5);
        
        for (int i = 0; i < 10; i++) {
            cache.put("user.id", i);
        }
        
        // Should only keep last 5 values (5-9)
        Map<String, Integer> stats = cache.getStats();
        assertThat(stats.get("user.id")).isEqualTo(5);
    }
}
```

---

## Acceptance Criteria

- ✅ ReferenceCache stores and retrieves values
- ✅ ReferenceGenerator selects random cached values
- ✅ LRU eviction when cache exceeds max size
- ✅ Thread-safe concurrent access
- ✅ Clear error when referencing non-existent structure
- ✅ All unit tests pass

---

## Usage Example

**Data Structures**:

`user.yaml`:
```yaml
name: user
data:
  id:
    datatype: int[1..999999]
  name:
    datatype: name
```

`order.yaml`:
```yaml
name: order
data:
  id:
    datatype: int[1..999999]
  user_id:
    datatype: ref[user.id]  # Reference to user.id
  amount:
    datatype: decimal[0.0..1000.0]
```

**Generation Order**: Must generate users before orders!

---

## Testing

Run tests:
```bash
./gradlew :generators:test
```

---

## Future Enhancements

- **Referential integrity validation**: Ensure all references exist
- **Cross-structure generation**: Auto-detect dependency order
- **Persistent cache**: Save cache to disk for multi-run consistency

---

**Completion Date**: [Mark when complete]
