# TASK-011: Generators Module - Locale-Specific Data

**Status**: 🔒 Blocked  
**Priority**: P1 (High)  
**Phase**: 2 - Data Generation  
**Dependencies**: TASK-010 (Datafaker Integration)  
**Human Supervision**: LOW (configuration-driven locales)

---

## Objective

Extend Datafaker integration to support all 62+ locales with locale-aware data generation based on the `geolocation` field in data structures.

---

## Background

After TASK-010 integrates Datafaker, this task ensures proper locale selection:
- `geolocation: italy` → Italian names, addresses, phone numbers
- `geolocation: japan` → Japanese names, addresses
- `geolocation: brazil` → Brazilian Portuguese data

Datafaker supports 62 locales including: en-US, en-GB, it, de, fr, es, pt-BR, ja, zh-CN, and more.

---

## Implementation Details

### Step 1: Create Locale Mapper

**File**: `generators/src/main/java/com/datagenerator/generators/LocaleMapper.java`

```java
package com.datagenerator.generators;

import java.util.Locale;
import java.util.Map;

/**
 * Maps geolocation strings to Java Locale objects for Datafaker.
 */
public class LocaleMapper {
    
    private static final Map<String, Locale> LOCALE_MAP = Map.ofEntries(
        // English variants
        Map.entry("en-us", Locale.US),
        Map.entry("en-gb", Locale.UK),
        Map.entry("en-ca", Locale.CANADA),
        
        // European locales
        Map.entry("italy", Locale.ITALY),
        Map.entry("it", Locale.ITALY),
        Map.entry("germany", Locale.GERMANY),
        Map.entry("de", Locale.GERMANY),
        Map.entry("france", Locale.FRANCE),
        Map.entry("fr", Locale.FRANCE),
        Map.entry("spain", new Locale("es", "ES")),
        Map.entry("es", new Locale("es", "ES")),
        
        // Americas
        Map.entry("brazil", new Locale("pt", "BR")),
        Map.entry("pt-br", new Locale("pt", "BR")),
        Map.entry("mexico", new Locale("es", "MX")),
        
        // Asia
        Map.entry("japan", Locale.JAPAN),
        Map.entry("ja", Locale.JAPAN),
        Map.entry("china", Locale.CHINA),
        Map.entry("zh-cn", Locale.CHINA),
        Map.entry("korea", Locale.KOREA),
        Map.entry("ko", Locale.KOREA),
        Map.entry("india", new Locale("en", "IN"))
        // Add all 62+ locales supported by Datafaker
    );
    
    /**
     * Map geolocation string to Locale, defaulting to en-US.
     */
    public static Locale map(String geolocation) {
        if (geolocation == null || geolocation.isBlank()) {
            return Locale.US;
        }
        
        String normalized = geolocation.toLowerCase().replace("_", "-");
        return LOCALE_MAP.getOrDefault(normalized, Locale.US);
    }
}
```

---

### Step 2: Update DatafakerGenerator

**File**: Update `generators/src/main/java/com/datagenerator/generators/DatafakerGenerator.java`

Ensure it uses `LocaleMapper` to create Faker instances:

```java
public DatafakerGenerator(PrimitiveType.Kind kind, String geolocation) {
    this.kind = kind;
    Locale locale = LocaleMapper.map(geolocation);
    this.faker = new Faker(locale);
    log.debug("Created DatafakerGenerator for {} with locale {}", kind, locale);
}
```

---

### Step 3: Write Unit Tests

**File**: `generators/src/test/java/com/datagenerator/generators/LocaleMapperTest.java`

```java
package com.datagenerator.generators;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class LocaleMapperTest {
    
    @Test
    void shouldMapCommonLocales() {
        assertThat(LocaleMapper.map("italy")).isEqualTo(Locale.ITALY);
        assertThat(LocaleMapper.map("it")).isEqualTo(Locale.ITALY);
        assertThat(LocaleMapper.map("japan")).isEqualTo(Locale.JAPAN);
        assertThat(LocaleMapper.map("brazil")).isEqualTo(new Locale("pt", "BR"));
    }
    
    @Test
    void shouldBeCaseInsensitive() {
        assertThat(LocaleMapper.map("ITALY")).isEqualTo(Locale.ITALY);
        assertThat(LocaleMapper.map("Italy")).isEqualTo(Locale.ITALY);
    }
    
    @Test
    void shouldDefaultToEnUS() {
        assertThat(LocaleMapper.map(null)).isEqualTo(Locale.US);
        assertThat(LocaleMapper.map("")).isEqualTo(Locale.US);
        assertThat(LocaleMapper.map("unknown")).isEqualTo(Locale.US);
    }
    
    @Test
    void shouldHandleUnderscoresAndHyphens() {
        assertThat(LocaleMapper.map("en-us")).isEqualTo(Locale.US);
        assertThat(LocaleMapper.map("en_us")).isEqualTo(Locale.US);
        assertThat(LocaleMapper.map("pt-br")).isEqualTo(new Locale("pt", "BR"));
    }
}
```

---

### Step 4: Integration Tests

**File**: `generators/src/test/java/com/datagenerator/generators/LocaleDataGenerationTest.java`

```java
package com.datagenerator.generators;

import com.datagenerator.core.type.PrimitiveType;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

class LocaleDataGenerationTest {
    
    private final Random random = new Random(12345);
    
    @Test
    void shouldGenerateItalianNames() {
        DatafakerGenerator generator = new DatafakerGenerator(
            PrimitiveType.Kind.NAME, "italy");
        
        String name = generator.generate(random);
        
        // Italian names should be generated
        assertThat(name).isNotBlank();
        // Can't assert exact values due to randomness, but verify it generates
    }
    
    @Test
    void shouldGenerateJapaneseNames() {
        DatafakerGenerator generator = new DatafakerGenerator(
            PrimitiveType.Kind.NAME, "japan");
        
        String name = generator.generate(random);
        
        assertThat(name).isNotBlank();
    }
    
    @Test
    void shouldGenerateBrazilianPhoneNumbers() {
        DatafakerGenerator generator = new DatafakerGenerator(
            PrimitiveType.Kind.PHONE_NUMBER, "brazil");
        
        String phone = generator.generate(random);
        
        assertThat(phone).isNotBlank();
    }
}
```

---

## Acceptance Criteria

- ✅ LocaleMapper supports all common geolocations
- ✅ Datafaker uses correct locale for generation
- ✅ Falls back to en-US for unknown locales
- ✅ Case-insensitive locale matching
- ✅ All unit and integration tests pass

---

## Testing

Run tests:
```bash
./gradlew :generators:test
```

---

## Documentation

Update copilot-instructions.md with complete locale list:
```markdown
**Supported Geolocations** (62+ locales):
- English: en-us, en-gb, en-ca, en-au
- European: italy/it, germany/de, france/fr, spain/es, portugal/pt
- Americas: brazil/pt-br, mexico/es-mx, argentina/es-ar
- Asian: japan/ja, china/zh-cn, korea/ko, india/en-in
- [Full list in LocaleMapper.java]
```

---

**Completion Date**: [Mark when complete]
