# TASK-007: Generators Module - Primitive Generators

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-004 (Type System), TASK-006 (Random Provider)  
**Human Supervision**: LOW (straightforward random generation)

---

## Objective

Implement generators for primitive data types with range constraints: char, int, decimal, boolean, date, timestamp, and enum.

---

## Background

Primitive generators produce random values within specified ranges:
- **char[3..50]**: Random alphanumeric strings of length 3-50
- **int[1..100]**: Random integers between 1 and 100
- **decimal[0.0..1.0]**: Random decimals between 0.0 and 1.0
- **boolean**: Random true/false
- **date[2020-01-01..2025-12-31]**: Random dates in range
- **timestamp[now-30d..now]**: Random timestamps in range
- **enum[A,B,C]**: Random selection from values

---

## Implementation Details

### Step 1: Create DataTypeGenerator Interface

**File**: `generators/src/main/java/com/datagenerator/generators/DataTypeGenerator.java`

```java
package com.datagenerator.generators;

import java.util.Random;

/**
 * Interface for generating data of a specific type.
 * 
 * @param <T> The type of data generated
 */
public interface DataTypeGenerator<T> {
    
    /**
     * Generate a random value.
     * 
     * @param random Random instance for generation
     * @return Generated value
     */
    T generate(Random random);
}
```

---

### Step 2: Implement CharGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/CharGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.util.Random;

/**
 * Generates random alphanumeric strings within a length range.
 */
@RequiredArgsConstructor
public class CharGenerator implements DataTypeGenerator<String> {
    
    private static final String ALPHANUMERIC = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    
    private final int minLength;
    private final int maxLength;
    
    @Override
    public String generate(Random random) {
        int length = minLength + random.nextInt(maxLength - minLength + 1);
        
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        
        return sb.toString();
    }
}
```

---

### Step 3: Implement IntGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/IntGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.util.Random;

/**
 * Generates random integers within a range.
 */
@RequiredArgsConstructor
public class IntGenerator implements DataTypeGenerator<Integer> {
    
    private final int min;
    private final int max;
    
    @Override
    public Integer generate(Random random) {
        return min + random.nextInt(max - min + 1);
    }
}
```

---

### Step 4: Implement DecimalGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/DecimalGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Generates random decimal numbers within a range.
 */
@RequiredArgsConstructor
public class DecimalGenerator implements DataTypeGenerator<BigDecimal> {
    
    private final BigDecimal min;
    private final BigDecimal max;
    private final int scale;
    
    public DecimalGenerator(BigDecimal min, BigDecimal max) {
        this(min, max, 2); // Default to 2 decimal places
    }
    
    @Override
    public BigDecimal generate(Random random) {
        BigDecimal range = max.subtract(min);
        BigDecimal randomFraction = BigDecimal.valueOf(random.nextDouble());
        BigDecimal value = min.add(range.multiply(randomFraction));
        
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
```

---

### Step 5: Implement BooleanGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/BooleanGenerator.java`

```java
package com.datagenerator.generators;

import java.util.Random;

/**
 * Generates random boolean values.
 */
public class BooleanGenerator implements DataTypeGenerator<Boolean> {
    
    @Override
    public Boolean generate(Random random) {
        return random.nextBoolean();
    }
}
```

---

### Step 6: Implement DateGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/DateGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.util.Random;

/**
 * Generates random dates within a range.
 */
@RequiredArgsConstructor
public class DateGenerator implements DataTypeGenerator<LocalDate> {
    
    private final LocalDate min;
    private final LocalDate max;
    
    @Override
    public LocalDate generate(Random random) {
        long minEpochDay = min.toEpochDay();
        long maxEpochDay = max.toEpochDay();
        
        long randomDay = minEpochDay + random.nextLong(maxEpochDay - minEpochDay + 1);
        
        return LocalDate.ofEpochDay(randomDay);
    }
}
```

---

### Step 7: Implement TimestampGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/TimestampGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Random;

/**
 * Generates random timestamps within a range.
 */
@RequiredArgsConstructor
public class TimestampGenerator implements DataTypeGenerator<Instant> {
    
    private final Instant min;
    private final Instant max;
    
    @Override
    public Instant generate(Random random) {
        long minEpochSecond = min.getEpochSecond();
        long maxEpochSecond = max.getEpochSecond();
        
        long randomSecond = minEpochSecond + random.nextLong(maxEpochSecond - minEpochSecond + 1);
        int randomNano = random.nextInt(1_000_000_000);
        
        return Instant.ofEpochSecond(randomSecond, randomNano);
    }
}
```

---

### Step 8: Implement EnumGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/EnumGenerator.java`

```java
package com.datagenerator.generators;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Random;

/**
 * Generates random values from a fixed set of enum values.
 */
@RequiredArgsConstructor
public class EnumGenerator implements DataTypeGenerator<String> {
    
    private final List<String> values;
    
    @Override
    public String generate(Random random) {
        int index = random.nextInt(values.size());
        return values.get(index);
    }
}
```

---

### Step 9: Write Unit Tests

**File**: `generators/src/test/java/com/datagenerator/generators/PrimitiveGeneratorsTest.java`

```java
package com.datagenerator.generators;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class PrimitiveGeneratorsTest {
    
    private final Random random = new Random(12345);
    
    @Test
    void charGeneratorShouldGenerateWithinLengthRange() {
        CharGenerator generator = new CharGenerator(5, 10);
        
        for (int i = 0; i < 100; i++) {
            String value = generator.generate(random);
            assertThat(value).hasSizeBetween(5, 10);
            assertThat(value).matches("[A-Za-z0-9]+");
        }
    }
    
    @Test
    void intGeneratorShouldGenerateWithinRange() {
        IntGenerator generator = new IntGenerator(10, 20);
        
        for (int i = 0; i < 100; i++) {
            Integer value = generator.generate(random);
            assertThat(value).isBetween(10, 20);
        }
    }
    
    @Test
    void decimalGeneratorShouldGenerateWithinRange() {
        DecimalGenerator generator = new DecimalGenerator(
            BigDecimal.valueOf(0.0), 
            BigDecimal.valueOf(1.0)
        );
        
        for (int i = 0; i < 100; i++) {
            BigDecimal value = generator.generate(random);
            assertThat(value).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
            assertThat(value.scale()).isEqualTo(2);
        }
    }
    
    @Test
    void booleanGeneratorShouldGenerateBothValues() {
        BooleanGenerator generator = new BooleanGenerator();
        
        boolean hasTrue = false;
        boolean hasFalse = false;
        
        for (int i = 0; i < 100; i++) {
            Boolean value = generator.generate(random);
            if (value) hasTrue = true;
            else hasFalse = true;
        }
        
        assertThat(hasTrue).isTrue();
        assertThat(hasFalse).isTrue();
    }
    
    @Test
    void dateGeneratorShouldGenerateWithinRange() {
        LocalDate min = LocalDate.of(2020, 1, 1);
        LocalDate max = LocalDate.of(2025, 12, 31);
        DateGenerator generator = new DateGenerator(min, max);
        
        for (int i = 0; i < 100; i++) {
            LocalDate value = generator.generate(random);
            assertThat(value).isBetween(min, max);
        }
    }
    
    @Test
    void timestampGeneratorShouldGenerateWithinRange() {
        Instant min = Instant.parse("2020-01-01T00:00:00Z");
        Instant max = Instant.parse("2025-12-31T23:59:59Z");
        TimestampGenerator generator = new TimestampGenerator(min, max);
        
        for (int i = 0; i < 100; i++) {
            Instant value = generator.generate(random);
            assertThat(value).isBetween(min, max);
        }
    }
    
    @Test
    void enumGeneratorShouldSelectFromValues() {
        List<String> values = List.of("ACTIVE", "INACTIVE", "PENDING");
        EnumGenerator generator = new EnumGenerator(values);
        
        for (int i = 0; i < 100; i++) {
            String value = generator.generate(random);
            assertThat(value).isIn(values);
        }
    }
}
```

---

## Acceptance Criteria

- ✅ All primitive generators implemented
- ✅ Generators respect range constraints
- ✅ Generated values are within specified bounds
- ✅ Thread-safe (stateless generators)
- ✅ All unit tests pass

---

## Testing

Run tests:
```bash
./gradlew :generators:test
```

---

**Completion Date**: [Mark when complete]
