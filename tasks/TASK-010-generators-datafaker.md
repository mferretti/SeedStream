# TASK-010: Generators Module - Datafaker Integration

**Status**: ✅ Complete  
**Priority**: P1 (High)  
**Phase**: 2 - Data Generation  
**Dependencies**: TASK-007 (Primitive Generators)  
**Human Supervision**: LOW (implementation is straightforward)
**Completed**: March 2026

---

## Objective

Integrate Datafaker 2.5.4 library (latest stable as of March 2026) to generate realistic locale-specific data (names, addresses, emails, phone numbers, etc.) for 62+ geolocations. Extend the type system to support semantic types beyond simple primitives.

---

## Background

Current implementation supports only primitive types with random values (e.g., `char[3..50]` generates random alphanumeric strings). Real-world testing requires realistic data:
- Italian names for `geolocation: italy` → "Mario Rossi", not "xKj9Pq2"
- Italian addresses → "Via Roma 123, Milano", not "abc xyz"
- Valid emails → "mario.rossi@example.com", not "char@char.com"

Datafaker provides 40+ core providers (name, address, email, phone, company, etc.) with support for 62 locales.

---

## Implementation Details

### Step 1: Add Datafaker Dependency

**File**: `generators/build.gradle.kts`

Add dependency:
```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    
    // Add Datafaker (latest stable version as of March 2026)
    implementation("net.datafaker:datafaker:2.5.4")
}
```

Run:
```bash
./gradlew generators:dependencies --configuration compileClasspath
# Verify Datafaker appears in dependency tree
```

---

### Step 2: Extend PrimitiveType.Kind Enum

**File**: `core/src/main/java/com/datagenerator/core/type/PrimitiveType.java`

**Current enum** (primitives only):
```java
public enum Kind {
    CHAR, INT, DECIMAL, BOOLEAN, DATE, TIMESTAMP
}
```

**New enum** (add semantic types):
```java
public enum Kind {
    // Existing primitives
    CHAR, INT, DECIMAL, BOOLEAN, DATE, TIMESTAMP,
    
    // Person semantic types
    NAME, FIRST_NAME, LAST_NAME, FULL_NAME, USERNAME, TITLE, OCCUPATION,
    
    // Address semantic types
    ADDRESS, STREET_NAME, STREET_NUMBER, CITY, STATE, POSTAL_CODE, COUNTRY,
    
    // Contact semantic types
    EMAIL, PHONE_NUMBER,
    
    // Finance semantic types
    COMPANY, CREDIT_CARD, IBAN, CURRENCY, PRICE,
    
    // Internet semantic types
    DOMAIN, URL, IPV4, IPV6, MAC_ADDRESS,
    
    // Code semantic types
    ISBN, UUID
}
```

**Rationale**: Semantic types have no range parameters (unlike `char[3..50]`), just type name.

---

### Step 3: Update TypeParser to Support Semantic Types

**File**: `core/src/main/java/com/datagenerator/core/type/TypeParser.java`

**Current**: Requires brackets for primitives (e.g., `char[3..50]`)

**New**: Support no-bracket syntax for semantic types (e.g., `name`, `email`, `address`)

**Algorithm**:
```java
public DataType parse(String typeSpec) {
    String trimmed = typeSpec.trim();
    
    // Check for brackets (primitive with range or complex types)
    if (trimmed.contains("[")) {
        // Existing logic for char[min..max], int[min..max], object[name], array[type, min..max]
        return parseBracketedType(trimmed);
    }
    
    // No brackets: semantic type or boolean
    return parseSemanticType(trimmed);
}

private DataType parseSemanticType(String typeSpec) {
    PrimitiveType.Kind kind = switch (typeSpec.toLowerCase()) {
        case "boolean" -> PrimitiveType.Kind.BOOLEAN;
        
        // Person types
        case "name" -> PrimitiveType.Kind.NAME;
        case "first_name", "firstname" -> PrimitiveType.Kind.FIRST_NAME;
        case "last_name", "lastname" -> PrimitiveType.Kind.LAST_NAME;
        case "full_name", "fullname" -> PrimitiveType.Kind.FULL_NAME;
        case "username" -> PrimitiveType.Kind.USERNAME;
        case "title" -> PrimitiveType.Kind.TITLE;
        case "occupation" -> PrimitiveType.Kind.OCCUPATION;
        
        // Address types
        case "address" -> PrimitiveType.Kind.ADDRESS;
        case "street_name", "streetname" -> PrimitiveType.Kind.STREET_NAME;
        case "street_number", "streetnumber" -> PrimitiveType.Kind.STREET_NUMBER;
        case "city" -> PrimitiveType.Kind.CITY;
        case "state" -> PrimitiveType.Kind.STATE;
        case "postal_code", "postalcode", "zipcode" -> PrimitiveType.Kind.POSTAL_CODE;
        case "country" -> PrimitiveType.Kind.COUNTRY;
        
        // Contact types
        case "email" -> PrimitiveType.Kind.EMAIL;
        case "phone_number", "phonenumber", "phone" -> PrimitiveType.Kind.PHONE_NUMBER;
        
        // Finance types
        case "company" -> PrimitiveType.Kind.COMPANY;
        case "credit_card", "creditcard" -> PrimitiveType.Kind.CREDIT_CARD;
        case "iban" -> PrimitiveType.Kind.IBAN;
        case "currency" -> PrimitiveType.Kind.CURRENCY;
        case "price" -> PrimitiveType.Kind.PRICE;
        
        // Internet types
        case "domain" -> PrimitiveType.Kind.DOMAIN;
        case "url" -> PrimitiveType.Kind.URL;
        case "ipv4" -> PrimitiveType.Kind.IPV4;
        case "ipv6" -> PrimitiveType.Kind.IPV6;
        case "mac_address", "macaddress" -> PrimitiveType.Kind.MAC_ADDRESS;
        
        // Code types
        case "isbn" -> PrimitiveType.Kind.ISBN;
        case "uuid" -> PrimitiveType.Kind.UUID;
        
        default -> throw new IllegalArgumentException("Unknown semantic type: " + typeSpec);
    };
    
    return new PrimitiveType(kind, null); // No range for semantic types
}
```

**Test** this parser change:
```java
@Test
void shouldParseSemanticTypes() {
    DataType nameType = parser.parse("name");
    assertThat(nameType).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) nameType).getKind()).isEqualTo(PrimitiveType.Kind.NAME);
    
    DataType emailType = parser.parse("email");
    assertThat(((PrimitiveType) emailType).getKind()).isEqualTo(PrimitiveType.Kind.EMAIL);
}
```

---

### Step 4: Create DatafakerGenerator Class

**File**: `generators/src/main/java/com/datagenerator/generators/DatafakerGenerator.java`

**Purpose**: Generate realistic data using Datafaker providers, respecting geolocation context.

**Class Structure**:
```java
package com.datagenerator.generators;

import com.datagenerator.core.type.PrimitiveType;
import java.util.Locale;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

@Slf4j
@RequiredArgsConstructor
public class DatafakerGenerator implements DataTypeGenerator<String> {
    
    private final PrimitiveType.Kind kind;
    private final String geolocation; // e.g., "italy", "usa", "france"
    
    @Override
    public String generate(Random random, TypeConfig config) {
        Faker faker = createFaker(random);
        
        return switch (kind) {
            // Person types
            case NAME -> faker.name().name();
            case FIRST_NAME -> faker.name().firstName();
            case LAST_NAME -> faker.name().lastName();
            case FULL_NAME -> faker.name().fullName();
            case USERNAME -> faker.name().username();
            case TITLE -> faker.name().title();
            case OCCUPATION -> faker.job().title();
            
            // Address types
            case ADDRESS -> faker.address().fullAddress();
            case STREET_NAME -> faker.address().streetName();
            case STREET_NUMBER -> faker.address().streetAddressNumber();
            case CITY -> faker.address().city();
            case STATE -> faker.address().state();
            case POSTAL_CODE -> faker.address().zipCode();
            case COUNTRY -> faker.address().country();
            
            // Contact types
            case EMAIL -> faker.internet().emailAddress();
            case PHONE_NUMBER -> faker.phoneNumber().phoneNumber();
            
            // Finance types
            case COMPANY -> faker.company().name();
            case CREDIT_CARD -> faker.finance().creditCard();
            case IBAN -> faker.finance().iban();
            case CURRENCY -> faker.currency().code();
            case PRICE -> faker.commerce().price();
            
            // Internet types
            case DOMAIN -> faker.internet().domainName();
            case URL -> faker.internet().url();
            case IPV4 -> faker.internet().ipV4Address();
            case IPV6 -> faker.internet().ipV6Address();
            case MAC_ADDRESS -> faker.internet().macAddress();
            
            // Code types
            case ISBN -> faker.code().isbn13();
            case UUID -> faker.internet().uuid();
            
            default -> throw new IllegalArgumentException(
                "Type " + kind + " is not supported by DatafakerGenerator"
            );
        };
    }
    
    private Faker createFaker(Random random) {
        Locale locale = parseGeolocation(geolocation);
        return new Faker(locale, random);
    }
    
    private Locale parseGeolocation(String geolocation) {
        if (geolocation == null || geolocation.isBlank()) {
            return Locale.ENGLISH; // Default to English
        }
        
        // Map common geolocation names to Java Locales
        return switch (geolocation.toLowerCase()) {
            case "italy", "italian" -> Locale.ITALY;
            case "usa", "us", "english" -> Locale.US;
            case "france", "french" -> Locale.FRANCE;
            case "germany", "german" -> Locale.GERMANY;
            case "spain", "spanish" -> new Locale("es", "ES");
            case "portugal", "portuguese" -> new Locale("pt", "PT");
            case "brazil" -> new Locale("pt", "BR");
            case "russia", "russian" -> new Locale("ru", "RU");
            case "china", "chinese" -> Locale.CHINA;
            case "japan", "japanese" -> Locale.JAPAN;
            case "korea", "korean" -> Locale.KOREA;
            // Add more as needed
            default -> {
                log.warn("Unknown geolocation '{}', defaulting to English", geolocation);
                yield Locale.ENGLISH;
            }
        };
    }
}
```

**Key Design Decisions**:
1. **Locale from geolocation**: Map human-readable names (e.g., "italy") to Java Locales
2. **Random seeding**: Pass Random instance to Faker for determinism
3. **Fallback to English**: Unknown geolocations default to English with warning

---

### Step 5: Update DataGeneratorFactory

**File**: `generators/src/main/java/com/datagenerator/generators/DataGeneratorFactory.java`

**Current factory** creates only primitive generators (CharGenerator, IntegerGenerator, etc.).

**New factory** must:
1. Detect semantic types (no range)
2. Create DatafakerGenerator with geolocation context
3. Maintain backward compatibility with primitive generators

**Algorithm**:
```java
public DataTypeGenerator<?> createGenerator(DataType type, String geolocation) {
    if (type instanceof PrimitiveType primitiveType) {
        PrimitiveType.Kind kind = primitiveType.getKind();
        
        // Check if semantic type (handled by Datafaker)
        if (isSemanticType(kind)) {
            return new DatafakerGenerator(kind, geolocation);
        }
        
        // Primitive types with ranges (existing logic)
        return switch (kind) {
            case CHAR -> new CharGenerator(primitiveType.getMinLength(), primitiveType.getMaxLength());
            case INT -> new IntegerGenerator(primitiveType.getMin(), primitiveType.getMax());
            case DECIMAL -> new DecimalGenerator(primitiveType.getMin(), primitiveType.getMax(), primitiveType.getScale());
            case BOOLEAN -> new BooleanGenerator();
            case DATE -> new DateGenerator(primitiveType.getStartDate(), primitiveType.getEndDate());
            case TIMESTAMP -> new TimestampGenerator(primitiveType.getStartTimestamp(), primitiveType.getEndTimestamp());
            default -> throw new IllegalArgumentException("Unsupported primitive type: " + kind);
        };
    }
    
    // Existing logic for EnumType, ObjectType, ArrayType
    // ...
}

private boolean isSemanticType(PrimitiveType.Kind kind) {
    return switch (kind) {
        case NAME, FIRST_NAME, LAST_NAME, FULL_NAME, USERNAME, TITLE, OCCUPATION,
             ADDRESS, STREET_NAME, STREET_NUMBER, CITY, STATE, POSTAL_CODE, COUNTRY,
             EMAIL, PHONE_NUMBER,
             COMPANY, CREDIT_CARD, IBAN, CURRENCY, PRICE,
             DOMAIN, URL, IPV4, IPV6, MAC_ADDRESS,
             ISBN, UUID -> true;
        default -> false;
    };
}
```

---

### Step 6: Propagate Geolocation Through Type System

**Challenge**: Geolocation is defined at structure level, but generators need it at field level.

**Solution**: Pass geolocation through the generation context.

**File**: `generators/src/main/java/com/datagenerator/generators/GeneratorContext.java`

**Add geolocation field**:
```java
@Value
@Builder
public class GeneratorContext {
    DataGeneratorFactory factory;
    StructureRegistry structureRegistry;
    String geolocation; // NEW: Add this field
}
```

**Update ObjectGenerator** to pass geolocation:
```java
public Map<String, Object> generate(Random random, TypeConfig config) {
    DataStructure structure = structureRegistry.getStructure(structureName);
    String geolocation = structure.getGeolocation(); // Get from structure definition
    
    Map<String, Object> record = new HashMap<>();
    for (FieldDefinition field : structure.getFields()) {
        DataTypeGenerator<?> generator = factory.createGenerator(field.getType(), geolocation);
        Object value = generator.generate(random, /* config */);
        record.put(field.getName(), value);
    }
    return record;
}
```

---

### Step 7: Update DataStructureParser

**File**: `schema/src/main/java/com/datagenerator/schema/DataStructureParser.java`

**Ensure geolocation is parsed** from YAML and stored in DataStructure:

```java
@Value
@Builder
public class DataStructure {
    String name;
    String geolocation; // Already exists (verify)
    List<FieldDefinition> fields;
}
```

**YAML example**:
```yaml
name: user
geolocation: italy
data:
  name:
    datatype: name      # Will use DatafakerGenerator with Italian locale
  email:
    datatype: email
```

---

## Acceptance Criteria

- ✅ Datafaker dependency added to generators module
- ✅ PrimitiveType.Kind enum extended with 20+ semantic types
- ✅ TypeParser supports no-bracket syntax for semantic types (`name`, `email`, etc.)
- ✅ DatafakerGenerator class created with locale support
- ✅ DataGeneratorFactory creates DatafakerGenerator for semantic types
- ✅ Geolocation propagates from structure definition to generators
- ✅ Generated data is realistic (e.g., Italian names for `geolocation: italy`)
- ✅ Generated data is reproducible (same seed → same data)
- ✅ Unknown geolocations default to English with warning log

---

## Testing Requirements

### Unit Tests

**File**: `generators/src/test/java/com/datagenerator/generators/DatafakerGeneratorTest.java`

**Test cases**:

1. **Test Determinism** (same seed → same output):
```java
@Test
void shouldGenerateDeterministicDataWithSameSeed() {
    long seed = 12345L;
    Random random1 = new Random(seed);
    Random random2 = new Random(seed);
    
    DatafakerGenerator generator = new DatafakerGenerator(PrimitiveType.Kind.NAME, "italy");
    
    String name1 = generator.generate(random1, null);
    String name2 = generator.generate(random2, null);
    
    assertThat(name1).isEqualTo(name2);
}
```

2. **Test Locale-Specific Output**:
```java
@Test
void shouldGenerateItalianNamesForItalyGeolocation() {
    Random random = new Random(12345L);
    DatafakerGenerator generator = new DatafakerGenerator(PrimitiveType.Kind.NAME, "italy");
    
    String name = generator.generate(random, null);
    
    // Italian names typically contain specific characters
    assertThat(name).isNotEmpty();
    assertThat(name).matches("[A-Za-zÀ-ÖØ-öø-ÿ ]+"); // Allows Italian characters
}
```

3. **Test All Semantic Types**:
```java
@ParameterizedTest
@EnumSource(value = PrimitiveType.Kind.class, names = {
    "NAME", "EMAIL", "PHONE_NUMBER", "ADDRESS", "COMPANY", "URL", "UUID"
})
void shouldGenerateValidDataForSemanticType(PrimitiveType.Kind kind) {
    Random random = new Random(12345L);
    DatafakerGenerator generator = new DatafakerGenerator(kind, "usa");
    
    String value = generator.generate(random, null);
    
    assertThat(value).isNotNull();
    assertThat(value).isNotEmpty();
}
```

4. **Test Geolocation Fallback**:
```java
@Test
void shouldFallbackToEnglishForUnknownGeolocation() {
    Random random = new Random(12345L);
    DatafakerGenerator generator = new DatafakerGenerator(PrimitiveType.Kind.NAME, "unknown_locale");
    
    String name = generator.generate(random, null);
    
    // Should generate valid name (English fallback)
    assertThat(name).isNotEmpty();
}
```

5. **Test TypeParser with Semantic Types**:
```java
@Test
void shouldParseSemanticTypesWithoutBrackets() {
    TypeParser parser = new TypeParser();
    
    DataType nameType = parser.parse("name");
    assertThat(nameType).isInstanceOf(PrimitiveType.class);
    assertThat(((PrimitiveType) nameType).getKind()).isEqualTo(PrimitiveType.Kind.NAME);
    
    DataType emailType = parser.parse("email");
    assertThat(((PrimitiveType) emailType).getKind()).isEqualTo(PrimitiveType.Kind.EMAIL);
}
```

**Minimum**: 15 new unit tests (5 above + 10 for other semantic types)

---

### Integration Tests

**File**: `generators/src/test/java/com/datagenerator/generators/DatafakerIntegrationTest.java`

**Test end-to-end generation** with YAML config:

```java
@Test
void shouldGenerateRealisticDataFromYamlStructure() throws Exception {
    // Given: YAML structure with semantic types
    String yaml = """
        name: user
        geolocation: italy
        data:
          name:
            datatype: name
          email:
            datatype: email
          phone:
            datatype: phone_number
        """;
    
    DataStructure structure = parser.parse(yaml);
    
    // When: Generate record
    DataGeneratorFactory factory = new DataGeneratorFactory();
    ObjectGenerator generator = new ObjectGenerator(structure.getName(), factory, structureRegistry);
    
    Random random = new Random(12345L);
    Map<String, Object> record = generator.generate(random, null);
    
    // Then: Verify realistic data
    assertThat(record).containsKeys("name", "email", "phone");
    assertThat(record.get("name")).asString().matches("[A-Za-zÀ-ÖØ-öø-ÿ ]+");
    assertThat(record.get("email")).asString().contains("@");
    assertThat(record.get("phone")).asString().matches("\\+?[0-9\\-\\s()]+");
}
```

---

## Files Created/Modified

### Created:
- `generators/src/main/java/com/datagenerator/generators/DatafakerGenerator.java`
- `generators/src/test/java/com/datagenerator/generators/DatafakerGeneratorTest.java`
- `generators/src/test/java/com/datagenerator/generators/DatafakerIntegrationTest.java`

### Modified:
- `generators/build.gradle.kts` (add Datafaker dependency)
- `core/src/main/java/com/datagenerator/core/type/PrimitiveType.java` (extend Kind enum)
- `core/src/main/java/com/datagenerator/core/type/TypeParser.java` (support no-bracket syntax)
- `generators/src/main/java/com/datagenerator/generators/DataGeneratorFactory.java` (route to DatafakerGenerator)
- `generators/src/main/java/com/datagenerator/generators/GeneratorContext.java` (add geolocation field)
- `generators/src/main/java/com/datagenerator/generators/ObjectGenerator.java` (pass geolocation)

---

## Common Issues & Solutions

**Issue**: Datafaker generates different data on each run  
**Solution**: Ensure Random instance is seeded deterministically and passed to Faker constructor

**Issue**: Italian names still look English  
**Solution**: Verify Locale is correctly passed to Faker. Check log for "Unknown geolocation" warnings

**Issue**: Test fails with "Unknown semantic type"  
**Solution**: Ensure TypeParser switch case includes the semantic type

**Issue**: Import errors for Datafaker classes  
**Solution**: Run `./gradlew generators:build` to download dependency, refresh IDE

---

## Completion Checklist

- [ ] Datafaker dependency added and verified
- [ ] PrimitiveType.Kind enum extended (20+ semantic types)
- [ ] TypeParser updated to support no-bracket syntax
- [ ] DatafakerGenerator class implemented
- [ ] Geolocation mapping implemented (10+ locales)
- [ ] DataGeneratorFactory routes semantic types to DatafakerGenerator
- [ ] GeneratorContext includes geolocation field
- [ ] ObjectGenerator passes geolocation to factory
- [ ] Unit tests pass (15+ tests)
- [ ] Integration test passes (realistic data validation)
- [ ] Determinism test passes (same seed → same output)
- [ ] Build succeeds: `./gradlew :generators:build`
- [ ] All tests pass: `./gradlew :generators:test`
- [ ] Code formatted: `./gradlew :generators:spotlessApply`

---

**Estimated Effort**: 6-8 hours  
**Complexity**: Medium (clear requirements, well-defined Datafaker API)
