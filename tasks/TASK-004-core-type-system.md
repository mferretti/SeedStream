# TASK-004: Core Module - Type System

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: None  
**Human Supervision**: LOW (core type definitions)

---

## Objective

Define the type system for representing data types: primitives (with ranges), objects (nested structures), arrays, enums, and references. This is the foundation for the generator system.

---

## Background

The type system represents all supported data types in YAML configurations:
- **Primitives**: `char[3..50]`, `int[1..100]`, `decimal[0.0..1.0]`, `boolean`, `date[2020-01-01..2025-12-31]`
- **Semantic**: `name`, `email`, `address`, `phone_number`
- **Objects**: `object[address]` (nested structures)
- **Arrays**: `array[int[1..10], 5..20]` (5-20 integers)
- **Enums**: `enum[ACTIVE,INACTIVE,PENDING]`
- **References**: `ref[user.id]` (foreign keys)

---

## Implementation Details

### Step 1: Create Base DataType Interface

**File**: `core/src/main/java/com/datagenerator/core/type/DataType.java`

```java
package com.datagenerator.core.type;

/**
 * Base interface for all data types in the type system.
 */
public interface DataType {
    
    /**
     * Get a human-readable representation of this type.
     */
    String toString();
}
```

---

### Step 2: Create PrimitiveType Class

**File**: `core/src/main/java/com/datagenerator/core/type/PrimitiveType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * Represents primitive data types with optional range constraints.
 */
@Value
public class PrimitiveType implements DataType {
    
    Kind kind;
    Range<?> range;
    
    public enum Kind {
        // Basic primitives with ranges
        CHAR, INT, DECIMAL, BOOLEAN, DATE, TIMESTAMP,
        
        // Semantic types (no ranges)
        NAME, FIRST_NAME, LAST_NAME, FULL_NAME, USERNAME, TITLE, OCCUPATION,
        ADDRESS, STREET_NAME, STREET_NUMBER, CITY, STATE, POSTAL_CODE, COUNTRY,
        EMAIL, PHONE_NUMBER,
        COMPANY, CREDIT_CARD, IBAN, CURRENCY, PRICE,
        DOMAIN, URL, IPV4, IPV6, MAC_ADDRESS,
        ISBN, UUID
    }
    
    /**
     * Range constraint for primitive types.
     */
    public interface Range<T extends Comparable<T>> {
        T getMin();
        T getMax();
        
        default boolean contains(T value) {
            return value.compareTo(getMin()) >= 0 && value.compareTo(getMax()) <= 0;
        }
    }
    
    @Value
    public static class IntRange implements Range<Integer> {
        Integer min;
        Integer max;
    }
    
    @Value
    public static class DecimalRange implements Range<BigDecimal> {
        BigDecimal min;
        BigDecimal max;
    }
    
    @Value
    public static class DateRange implements Range<LocalDate> {
        LocalDate min;
        LocalDate max;
    }
    
    @Value
    public static class TimestampRange implements Range<Instant> {
        Instant min;
        Instant max;
    }
    
    @Override
    public String toString() {
        if (range == null) {
            return kind.name().toLowerCase();
        }
        return kind.name().toLowerCase() + "[" + range.getMin() + ".." + range.getMax() + "]";
    }
}
```

---

### Step 3: Create ObjectType Class

**File**: `core/src/main/java/com/datagenerator/core/type/ObjectType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a nested object type referencing another structure.
 */
@Value
public class ObjectType implements DataType {
    
    String structureName;
    
    @Override
    public String toString() {
        return "object[" + structureName + "]";
    }
}
```

---

### Step 4: Create ArrayType Class

**File**: `core/src/main/java/com/datagenerator/core/type/ArrayType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents an array type with variable length.
 */
@Value
public class ArrayType implements DataType {
    
    DataType elementType;
    int minLength;
    int maxLength;
    
    @Override
    public String toString() {
        return "array[" + elementType + ", " + minLength + ".." + maxLength + "]";
    }
}
```

---

### Step 5: Create EnumType Class

**File**: `core/src/main/java/com/datagenerator/core/type/EnumType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;
import java.util.List;

/**
 * Represents an enumeration type with fixed set of values.
 */
@Value
public class EnumType implements DataType {
    
    List<String> values;
    
    @Override
    public String toString() {
        return "enum[" + String.join(",", values) + "]";
    }
}
```

---

### Step 6: Create ReferenceType Class

**File**: `core/src/main/java/com/datagenerator/core/type/ReferenceType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a reference to another generated record (foreign key).
 */
@Value
public class ReferenceType implements DataType {
    
    String targetStructure;
    String targetField;
    
    @Override
    public String toString() {
        return "ref[" + targetStructure + "." + targetField + "]";
    }
}
```

---

### Step 7: Write Unit Tests

**File**: `core/src/test/java/com/datagenerator/core/type/TypeSystemTest.java`

```java
package com.datagenerator.core.type;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TypeSystemTest {
    
    @Test
    void shouldCreatePrimitiveTypeWithRange() {
        var range = new PrimitiveType.IntRange(1, 100);
        var type = new PrimitiveType(PrimitiveType.Kind.INT, range);
        
        assertThat(type.getKind()).isEqualTo(PrimitiveType.Kind.INT);
        assertThat(type.getRange()).isEqualTo(range);
        assertThat(type.toString()).isEqualTo("int[1..100]");
    }
    
    @Test
    void shouldCreateSemanticTypeWithoutRange() {
        var type = new PrimitiveType(PrimitiveType.Kind.EMAIL, null);
        
        assertThat(type.getKind()).isEqualTo(PrimitiveType.Kind.EMAIL);
        assertThat(type.getRange()).isNull();
        assertThat(type.toString()).isEqualTo("email");
    }
    
    @Test
    void shouldValidateRangeContains() {
        var range = new PrimitiveType.IntRange(10, 20);
        
        assertThat(range.contains(15)).isTrue();
        assertThat(range.contains(10)).isTrue();
        assertThat(range.contains(20)).isTrue();
        assertThat(range.contains(9)).isFalse();
        assertThat(range.contains(21)).isFalse();
    }
    
    @Test
    void shouldCreateObjectType() {
        var type = new ObjectType("address");
        
        assertThat(type.getStructureName()).isEqualTo("address");
        assertThat(type.toString()).isEqualTo("object[address]");
    }
    
    @Test
    void shouldCreateArrayType() {
        var elementType = new PrimitiveType(PrimitiveType.Kind.INT, 
            new PrimitiveType.IntRange(1, 100));
        var type = new ArrayType(elementType, 5, 10);
        
        assertThat(type.getElementType()).isEqualTo(elementType);
        assertThat(type.getMinLength()).isEqualTo(5);
        assertThat(type.getMaxLength()).isEqualTo(10);
        assertThat(type.toString()).isEqualTo("array[int[1..100], 5..10]");
    }
    
    @Test
    void shouldCreateEnumType() {
        var type = new EnumType(List.of("ACTIVE", "INACTIVE", "PENDING"));
        
        assertThat(type.getValues()).containsExactly("ACTIVE", "INACTIVE", "PENDING");
        assertThat(type.toString()).isEqualTo("enum[ACTIVE,INACTIVE,PENDING]");
    }
    
    @Test
    void shouldCreateReferenceType() {
        var type = new ReferenceType("user", "id");
        
        assertThat(type.getTargetStructure()).isEqualTo("user");
        assertThat(type.getTargetField()).isEqualTo("id");
        assertThat(type.toString()).isEqualTo("ref[user.id]");
    }
}
```

---

## Acceptance Criteria

- ✅ All type classes implement DataType interface
- ✅ PrimitiveType supports both ranges and semantic types
- ✅ ObjectType references other structures by name
- ✅ ArrayType has element type and length bounds
- ✅ EnumType contains list of valid values
- ✅ ReferenceType points to target structure and field
- ✅ All types have meaningful toString() representation
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :core:test
```

---

**Completion Date**: [Mark when complete]
