# TASK-041: Datafaker Plugin Architecture - Extensible Type Registry

**Status**: ✅ Complete
**Priority**: P2 (Medium)
**Phase**: Future Enhancement
**Dependencies**: TASK-010 (Datafaker Integration)
**Human Supervision**: MEDIUM (architectural changes require careful review)
**Completion Date**: March 9, 2026
**Related**: US-041

---

## Objective

Implement a plugin architecture that allows users to register custom Datafaker types at runtime without modifying core code. This enables unlimited extensibility while keeping the core codebase lean with only commonly-used types built-in.

---

## Background

### Current State (March 2026)
- **28 built-in semantic types** (~25% of Datafaker's 110+ providers)
- **Fixed type system**: Adding new types requires enum changes, parser updates, generator updates
- **Release bottleneck**: Users must wait for releases to get new types
- **Code bloat concern**: Adding all 110+ types would create maintenance burden for rarely-used providers

### Problem
- **Medical sector** needs: medicine_name, disease, hospital
- **Entertainment apps** need: book_title, movie_title, music_genre
- **Food/Beverage** need: food_dish, beer_style, cocktail
- **Gaming** needs: pokemon, superhero, video_game
- **Education** needs: university, degree, major

Adding all these would require ~200 lines of enum values, ~400 lines of parser cases, and extensive testing.

### Solution: Plugin Architecture
Enable users to register custom types dynamically:
```java
DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
```

---

## Implementation Details

### Step 1: Create DatafakerRegistry (Core)

**New File**: `generators/src/main/java/com/datagenerator/generators/semantic/DatafakerRegistry.java`

```java
package com.datagenerator.generators.semantic;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;

/**
 * Thread-safe registry for custom Datafaker type mappings.
 * 
 * <p>Allows runtime registration of custom types beyond the 28 built-in semantic types.
 * Users can register any Datafaker provider and use it in YAML configurations.
 * 
 * <p><b>Example:</b>
 * <pre>
 * // Register custom types
 * DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
 * DatafakerRegistry.register("beer_style", (faker, random) -> faker.beer().style());
 * 
 * // Use in YAML:
 * favorite_pokemon:
 *   datatype: pokemon  # Custom type!
 * </pre>
 * 
 * <p><b>Thread Safety:</b> All operations are thread-safe using ConcurrentHashMap.
 */
@Slf4j
public class DatafakerRegistry {

  /** Map of custom type name → generator function */
  private static final ConcurrentHashMap<String, DatafakerFunction> registry =
      new ConcurrentHashMap<>();

  /**
   * Functional interface for custom Datafaker generators.
   * 
   * <p>Takes a Faker instance and Random, returns generated String value.
   */
  @FunctionalInterface
  public interface DatafakerFunction {
    /**
     * Generate a value using Datafaker.
     * 
     * @param faker Faker instance (locale-aware, seeded)
     * @param random Thread-local Random for additional entropy
     * @return Generated string value
     */
    String generate(Faker faker, Random random);
  }

  /**
   * Register a custom Datafaker type.
   * 
   * @param typeName Type name (e.g., "pokemon", "beer_style") - lowercase recommended
   * @param function Generator function
   * @throws IllegalArgumentException if typeName is blank or function is null
   */
  public static void register(String typeName, DatafakerFunction function) {
    if (typeName == null || typeName.isBlank()) {
      throw new IllegalArgumentException("Type name cannot be null or blank");
    }
    if (function == null) {
      throw new IllegalArgumentException("Function cannot be null");
    }

    String normalized = typeName.trim().toLowerCase();
    DatafakerFunction previous = registry.put(normalized, function);

    if (previous != null) {
      log.warn("Overwriting existing custom type: {}", normalized);
    } else {
      log.info("Registered custom Datafaker type: {}", normalized);
    }
  }

  /**
   * Check if a type is registered.
   * 
   * @param typeName Type name to check
   * @return true if registered (custom type exists)
   */
  public static boolean isRegistered(String typeName) {
    if (typeName == null) {
      return false;
    }
    return registry.containsKey(typeName.trim().toLowerCase());
  }

  /**
   * Generate a value for a custom type.
   * 
   * @param typeName Custom type name
   * @param faker Faker instance (locale-aware, seeded)
   * @param random Thread-local Random
   * @return Generated value
   * @throws IllegalArgumentException if type not registered
   */
  public static String generate(String typeName, Faker faker, Random random) {
    String normalized = typeName.trim().toLowerCase();
    DatafakerFunction function = registry.get(normalized);

    if (function == null) {
      throw new IllegalArgumentException(
          "Custom type not registered: " + typeName + ". "
              + "Use DatafakerRegistry.register() or check spelling.");
    }

    try {
      String value = function.generate(faker, random);
      if (value == null) {
        log.warn("Custom type '{}' generated null value, returning empty string", typeName);
        return "";
      }
      return value;
    } catch (Exception e) {
      log.error("Error generating custom type '{}': {}", typeName, e.getMessage(), e);
      throw new RuntimeException("Failed to generate custom type: " + typeName, e);
    }
  }

  /**
   * Get all registered type names.
   * 
   * @return Immutable set of registered type names
   */
  public static Set<String> listTypes() {
    return Set.copyOf(registry.keySet());
  }

  /**
   * Clear all registrations (for testing only).
   * 
   * <p><b>Warning:</b> Not recommended in production. Use for test isolation only.
   */
  public static void clear() {
    int size = registry.size();
    registry.clear();
    log.debug("Cleared {} custom type registrations", size);
  }

  /**
   * Get count of registered types.
   * 
   * @return Number of custom types registered
   */
  public static int size() {
    return registry.size();
  }
}
```

---

### Step 2: Create CustomDatafakerType

**New File**: `core/src/main/java/com/datagenerator/core/type/CustomDatafakerType.java`

```java
package com.datagenerator.core.type;

import lombok.Value;

/**
 * Represents a custom Datafaker type registered via DatafakerRegistry.
 * 
 * <p>Unlike built-in semantic types (enumerated in PrimitiveType.Kind), custom types
 * are registered dynamically at runtime and resolved through the registry.
 * 
 * <p><b>Example:</b>
 * <pre>
 * DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
 * 
 * CustomDatafakerType pokemonType = new CustomDatafakerType("pokemon");
 * // Later used in generation: DatafakerRegistry.generate("pokemon", faker, random)
 * </pre>
 */
@Value
public class CustomDatafakerType implements DataType {
  /** Custom type name (e.g., "pokemon", "beer_style"). */
  String typeName;

  /**
   * Create a custom Datafaker type.
   * 
   * @param typeName Name of the custom type (should be registered in DatafakerRegistry)
   */
  public CustomDatafakerType(String typeName) {
    if (typeName == null || typeName.isBlank()) {
      throw new IllegalArgumentException("Type name cannot be null or blank");
    }
    this.typeName = typeName.trim().toLowerCase();
  }

  @Override
  public String describe() {
    return "custom[" + typeName + "]";
  }
}
```

---

### Step 3: Update TypeParser

**File**: `core/src/main/java/com/datagenerator/core/type/TypeParser.java`

**Changes in `parseSemanticType()` method:**

```java
private DataType parseSemanticType(String typeSpec) {
  // First, try built-in types
  PrimitiveType.Kind kind = parseBuiltInSemanticType(typeSpec);
  if (kind != null) {
    return new PrimitiveType(kind, null, null);
  }

  // Check if it's a registered custom type
  if (DatafakerRegistry.isRegistered(typeSpec)) {
    return new CustomDatafakerType(typeSpec);
  }

  // Not found
  throw new TypeParseException(
      "Unknown type: '"
          + typeSpec
          + "'. Not a built-in type or registered custom type. "
          + "Available built-in types: name, email, city, etc. "
          + "Register custom types with DatafakerRegistry.register()");
}

/**
 * Extract built-in semantic type parsing to separate method.
 */
private PrimitiveType.Kind parseBuiltInSemanticType(String typeSpec) {
  return switch (typeSpec.toLowerCase()) {
    case "boolean" -> PrimitiveType.Kind.BOOLEAN;
    case "name" -> PrimitiveType.Kind.NAME;
    // ... all existing built-in cases ...
    default -> null; // Not a built-in type
  };
}
```

**Import addition:**
```java
import com.datagenerator.generators.semantic.DatafakerRegistry;
```

**Note:** This creates a dependency from `core` → `generators`, which reverses the current flow. We need to address this.

**Alternative Design** (to avoid circular dependency):
- Move `DatafakerRegistry` to `core` module instead of `generators`
- Or: Use a callback/provider pattern where `TypeParser` accepts a custom type checker

**Recommended:** Move `DatafakerRegistry` to core module:
```
core/src/main/java/com/datagenerator/core/registry/DatafakerRegistry.java
```

This keeps the core module independent and allows TypeParser to check the registry without depending on generators.

---

### Step 4: Update DatafakerGenerator

**File**: `generators/src/main/java/com/datagenerator/generators/semantic/DatafakerGenerator.java`

**Update `supports()` method:**
```java
@Override
public boolean supports(DataType type) {
  if (type instanceof PrimitiveType primitiveType) {
    return isSemanticType(primitiveType.getKind());
  }
  
  // Support custom Datafaker types
  if (type instanceof CustomDatafakerType) {
    return true;
  }
  
  return false;
}
```

**Update `generate()` method:**
```java
@Override
public Object generate(Random random, DataType type) {
  // Handle custom types first
  if (type instanceof CustomDatafakerType customType) {
    String geolocation = GeneratorContext.getGeolocation();
    Locale locale = LocaleMapper.map(geolocation);
    Faker faker = FakerCache.getOrCreate(locale, random);
    
    return DatafakerRegistry.generate(customType.getTypeName(), faker, random);
  }
  
  // Existing built-in type logic
  if (!(type instanceof PrimitiveType primitiveType)) {
    throw new GeneratorException("DatafakerGenerator only supports PrimitiveType, got: " + type);
  }
  
  // ... rest of existing logic ...
}
```

**Import additions:**
```java
import com.datagenerator.core.type.CustomDatafakerType;
import com.datagenerator.core.registry.DatafakerRegistry;
```

---

### Step 5: CLI Integration (Optional - Can be Phase 2)

**File**: `cli/src/main/java/com/datagenerator/cli/ExecuteCommand.java`

**Add `--registry` option:**
```java
@Option(
    names = {"--registry"},
    description = "Path to custom type registry YAML file")
private String registryPath;
```

**In `call()` method, before generation:**
```java
if (registryPath != null) {
  loadCustomRegistry(registryPath);
}

private void loadCustomRegistry(String path) {
  // Load YAML, parse custom type definitions, register them
  // This is Phase 2 work - deferred for now
  log.info("Custom registry loading not yet implemented");
}
```

**Add `list-types` command:**
```java
@Command(name = "list-types", description = "List all available Datafaker types")
public class ListTypesCommand implements Callable<Integer> {
  
  @Override
  public Integer call() {
    System.out.println("\n=== Built-in Datafaker Types (28) ===");
    // List all PrimitiveType.Kind semantic types
    
    System.out.println("\n=== Custom Datafaker Types (" + DatafakerRegistry.size() + ") ===");
    Set<String> custom = DatafakerRegistry.listTypes();
    if (custom.isEmpty()) {
      System.out.println("(none registered)");
    } else {
      custom.stream().sorted().forEach(System.out::println);
    }
    
    return 0;
  }
}
```

---

## Testing Strategy

### Unit Tests - DatafakerRegistry (12 tests)

**File**: `generators/src/test/java/com/datagenerator/generators/semantic/DatafakerRegistryTest.java`

```java
@Test
void shouldRegisterCustomType() {
  DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
  assertThat(DatafakerRegistry.isRegistered("pokemon")).isTrue();
}

@Test
void shouldGenerateCustomTypeValue() {
  DatafakerRegistry.register("test_type", (faker, random) -> "test_value");
  Faker faker = new Faker();
  Random random = new Random(42);
  
  String value = DatafakerRegistry.generate("test_type", faker, random);
  assertThat(value).isEqualTo("test_value");
}

@Test
void shouldThrowWhenGeneratingUnregisteredType() {
  assertThatThrownBy(() -> DatafakerRegistry.generate("nonexistent", new Faker(), new Random()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("not registered");
}

@Test
void shouldBeThreadSafe() {
  // Concurrent registration from 10 threads
}

@Test
void shouldNormalizeTypeNames() {
  DatafakerRegistry.register("  PokeMon  ", (faker, random) -> "value");
  assertThat(DatafakerRegistry.isRegistered("pokemon")).isTrue();
  assertThat(DatafakerRegistry.isRegistered("POKEMON")).isTrue();
}
```

### Unit Tests - TypeParser (5 tests)

```java
@Test
void shouldParseCustomDatafakerType() {
  DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
  DataType type = TypeParser.parse("pokemon");
  assertThat(type).isInstanceOf(CustomDatafakerType.class);
  assertThat(((CustomDatafakerType) type).getTypeName()).isEqualTo("pokemon");
}

@Test
void shouldFailForUnregisteredCustomType() {
  assertThatThrownBy(() -> TypeParser.parse("nonexistent"))
      .isInstanceOf(TypeParseException.class)
      .hasMessageContaining("Unknown type");
}
```

### Integration Tests (8 tests)

```java
@Test
void shouldGenerateStructureWithCustomTypes() {
  // Register custom types
  DatafakerRegistry.register("pokemon", (faker, random) -> faker.pokemon().name());
  DatafakerRegistry.register("beer_style", (faker, random) -> faker.beer().style());
  
  // Parse structure with custom types
  DataStructure structure = parser.parse("custom_structure.yaml");
  
  // Generate 100 records
  List<Map<String, Object>> records = generator.generate(structure, 100);
  
  // Verify custom fields are populated
  assertThat(records.get(0)).containsKey("favorite_pokemon");
  assertThat(records.get(0).get("favorite_pokemon")).isNotNull();
}
```

---

## Acceptance Criteria

- [ ] `DatafakerRegistry` class in core module with thread-safe operations
- [ ] `register(name, function)` method
- [ ] `isRegistered(name)` method returns true/false
- [ ] `generate(name, faker, random)` method invokes registered function
- [ ] `listTypes()` returns all registered type names
- [ ] `clear()` removes all registrations (testing only)
- [ ] `CustomDatafakerType` class in core module
- [ ] TypeParser checks registry for unknown types
- [ ] TypeParser throws clear error for unregistered types
- [ ] DatafakerGenerator supports CustomDatafakerType
- [ ] 20+ unit tests, coverage >= 90%
- [ ] 8+ integration tests (end-to-end with custom types)
- [ ] Thread-safety validated (concurrent registration)
- [ ] No performance regression for built-in types
- [ ] JavaDoc documentation for all public APIs
- [ ] README section: "Registering Custom Types"
- [ ] Example code snippets in README
- [ ] All existing tests still pass (backward compatibility)

---

## Follow-Up Tasks (Optional Phase 2)

1. **TASK-042**: YAML Registry Loader
   - Parse `custom-types.yaml` files
   - CLI `--registry` flag implementation
   - Expression evaluation for Faker method calls

2. **TASK-043**: CLI list-types Command
   - Pretty-print built-in + custom types
   - Category grouping
   - Search/filter capabilities

3. **TASK-044**: Example Registry Files
   - Medical types registry
   - Entertainment types registry
   - Food/beverage types registry
   - Gaming types registry

---

## Non-Goals

- ❌ Reflection-based Datafaker method discovery (too fragile, Java version dependent)
- ❌ Type validation at registration time (validate at generation)
- ❌ Hot-reload of registry during generation (restart required)
- ❌ Graphical registry editor (CLI/YAML sufficient)
- ❌ Type versioning or migration tools

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Circular dependency (core → generators) | Move DatafakerRegistry to core module |
| Performance overhead | Benchmark lookup time, use ConcurrentHashMap |
| Type name collisions | Warn on overwrite, case-insensitive normalization |
| Thread-safety bugs | Comprehensive concurrency tests |
| User confusion | Clear error messages, documentation, examples |

---

## Definition of Done

- [ ] All acceptance criteria met
- [ ] Code reviewed and approved
- [ ] 28+ tests passing (20 unit + 8 integration)
- [ ] Test coverage >= 90% for new code
- [ ] Performance: Registry lookup <1 microsecond
- [ ] Documentation complete (JavaDoc + README)
- [ ] Example code in README
- [ ] No breaking changes (all existing tests pass)
- [ ] Spotless formatting applied
- [ ] PR merged to main branch
