# US-041: Datafaker Plugin Architecture for Extensible Type Registry

**Status**: ⏸️ Not Started  
**Priority**: P2 (Medium)  
**Phase**: Future Enhancement  
**Dependencies**: US-010 (Datafaker Integration)  
**Estimated Effort**: 16-20h  
**Target Release**: Post v1.0

---

## User Story

As a **power user**, I want **to register custom Datafaker types dynamically** so that **I can generate data using any Datafaker provider without waiting for built-in support**, enabling specialized use cases like Pokemon names, beer styles, medical terms, or domain-specific data.

---

## Business Value

### Current Limitation
- Built-in support for only **28 semantic types** (~25% of Datafaker's 110+ providers)
- Adding new types requires:
  - Enum modification in `PrimitiveType.Kind`
  - Parser updates in `TypeParser`
  - Generator updates in `DatafakerGenerator`
  - Comprehensive testing
  - Code review and release cycle
- Users blocked on niche types (medical, entertainment, food, games, etc.)

### With Plugin Architecture
- **User empowerment**: Register any Datafaker provider at runtime
- **Zero code changes**: No enum modifications or recompilation needed
- **Rapid prototyping**: Test new data types immediately
- **Community contributions**: Share custom type registries
- **Maintenance reduction**: Core stays lean, users add what they need

---

## Acceptance Criteria

### Core Plugin System
- ✅ `DatafakerRegistry` allows runtime type registration
- ✅ Support for custom type names (e.g., "pokemon", "beer_style", "medicine")
- ✅ Lambda-based provider functions: `(Faker, Random) -> String`
- ✅ Thread-safe registration (ConcurrentHashMap or synchronized)
- ✅ Fallback to built-in types if custom type not found

### YAML Configuration Support
- ✅ Support custom types in data structure YAML
  ```yaml
  data:
    favorite_pokemon:
      datatype: pokemon  # Custom registered type
    beer_preference:
      datatype: beer_style
  ```
- ✅ TypeParser recognizes custom types dynamically
- ✅ Validation: Error if type not registered (fail-fast)

### Registration API
- ✅ **Java API**: `DatafakerRegistry.register(name, function)`
  ```java
  DatafakerRegistry.register("pokemon", 
      (faker, random) -> faker.pokemon().name());
  ```
- ✅ **YAML Configuration**: External registry file (optional)
  ```yaml
  custom_types:
    pokemon: "faker.pokemon().name()"
    beer_style: "faker.beer().style()"
    medicine: "faker.medical().medicineName()"
  ```
- ✅ **Programmatic**: CLI flag `--registry path/to/registry.yaml`

### Discovery & Documentation
- ✅ List all registered types: `DatafakerRegistry.listTypes()`
- ✅ CLI command: `./gradlew :cli:run --args="list-types"`
- ✅ Built-in types included in listing
- ✅ Documentation: README section on custom type registration

---

## Technical Design

### Architecture Overview

```
┌─────────────────────────────────────────────┐
│          Application Layer                   │
│  (User YAML configs + Custom registrations) │
└──────────────────┬───────────────────────────┘
                   │
┌──────────────────▼───────────────────────────┐
│         TypeParser (Enhanced)                 │
│  1. Check built-in types                     │
│  2. Check DatafakerRegistry for custom types │
│  3. Fail if not found                        │
└──────────────────┬───────────────────────────┘
                   │
┌──────────────────▼───────────────────────────┐
│        DatafakerRegistry                      │
│  • ConcurrentHashMap<String, Function>       │
│  • register(name, function)                  │
│  • isRegistered(name) → boolean              │
│  • get(name) → Function<Faker, String>       │
│  • listTypes() → Set<String>                 │
└──────────────────┬───────────────────────────┘
                   │
┌──────────────────▼───────────────────────────┐
│      DatafakerGenerator (Enhanced)            │
│  1. Check if built-in type                   │
│  2. If not, invoke custom function           │
│  3. Cache Faker per thread (existing)        │
└───────────────────────────────────────────────┘
```

### Key Components

#### 1. DatafakerRegistry (New Class)
```java
package com.datagenerator.generators.semantic;

public class DatafakerRegistry {
    private static final ConcurrentHashMap<String, DatafakerFunction> registry = 
        new ConcurrentHashMap<>();
    
    @FunctionalInterface
    public interface DatafakerFunction {
        String generate(Faker faker, Random random);
    }
    
    public static void register(String typeName, DatafakerFunction function) {
        // Validation, registration
    }
    
    public static boolean isRegistered(String typeName) {
        // Check if type exists
    }
    
    public static String generate(String typeName, Faker faker, Random random) {
        // Invoke registered function
    }
    
    public static Set<String> listTypes() {
        // Return all registered type names
    }
    
    public static void clear() {
        // For testing: clear all registrations
    }
}
```

#### 2. Enhanced TypeParser
```java
// In parseSemanticType()
PrimitiveType.Kind kind = parseBuiltInType(typeSpec);
if (kind != null) {
    return new PrimitiveType(kind, null, null);
}

// Check custom registry
if (DatafakerRegistry.isRegistered(typeSpec)) {
    return new CustomDatafakerType(typeSpec); // NEW type class
}

throw new TypeParseException("Unknown type: " + typeSpec);
```

#### 3. CustomDatafakerType (New Class)
```java
package com.datagenerator.core.type;

@Value
public class CustomDatafakerType implements DataType {
    String typeName;
    
    @Override
    public String describe() {
        return "custom[" + typeName + "]";
    }
}
```

#### 4. Enhanced DatafakerGenerator
```java
@Override
public boolean supports(DataType type) {
    if (type instanceof PrimitiveType primitiveType) {
        return isSemanticType(primitiveType.getKind());
    }
    if (type instanceof CustomDatafakerType) {
        return true; // Support all custom types
    }
    return false;
}

@Override
public Object generate(Random random, DataType type) {
    if (type instanceof CustomDatafakerType customType) {
        Faker faker = FakerCache.getOrCreate(locale, random);
        return DatafakerRegistry.generate(customType.getTypeName(), faker, random);
    }
    // ... existing built-in type logic
}
```

---

## Implementation Plan

### Phase 1: Core Registry (8-10h)
1. Create `DatafakerRegistry` class with ConcurrentHashMap
2. Implement register/get/list/clear methods
3. Thread-safety tests (10+ tests)
4. Documentation: JavaDoc with examples

### Phase 2: Type System Integration (4-6h)
1. Create `CustomDatafakerType` class
2. Update `TypeParser` to check registry
3. Update `DatafakerGenerator` to support custom types
4. Unit tests: 15+ tests covering all paths

### Phase 3: YAML & CLI Support (4-6h)
1. YAML registry parser (optional feature)
2. CLI `--registry` flag
3. CLI `list-types` command
4. Integration tests: 8+ tests

### Phase 4: Documentation & Examples (2-3h)
1. README section: "Registering Custom Datafaker Types"
2. Example registry YAML files (medical, entertainment, food)
3. Code examples in multiple languages
4. Migration guide for built-in → custom types

---

## Examples

### Java API Registration
```java
// In application initialization or CLI
DatafakerRegistry.register("pokemon", 
    (faker, random) -> faker.pokemon().name());

DatafakerRegistry.register("beer_style", 
    (faker, random) -> faker.beer().style());

DatafakerRegistry.register("medicine", 
    (faker, random) -> faker.medical().medicineName());
```

### YAML Configuration
```yaml
# custom-types.yaml
pokemon: "faker.pokemon().name()"
beer_style: "faker.beer().style()"
beer_name: "faker.beer().name()"
cocktail: "faker.beer().hop()"
superhero: "faker.superhero().name()"
book_title: "faker.book().title()"
artist: "faker.artist().name()"
medicine: "faker.medical().medicineName()"
```

### Data Structure Usage
```yaml
# data structure
name: rpg_character
geolocation: usa
data:
  name:
    datatype: name  # Built-in
  class:
    datatype: enum[Warrior,Mage,Rogue,Paladin]
  favorite_pokemon:
    datatype: pokemon  # Custom!
  favorite_beer:
    datatype: beer_style  # Custom!
```

### CLI Usage
```bash
# Register custom types and generate
./gradlew :cli:run --args="execute \
  --job config/jobs/rpg_character.yaml \
  --registry config/custom-types.yaml \
  --count 100"

# List all available types
./gradlew :cli:run --args="list-types"
# Output:
# Built-in types (28):
#   name, email, city, ...
# Custom types (5):
#   pokemon, beer_style, medicine, superhero, book_title
```

---

## Testing Requirements

### Unit Tests (25+ tests)
- DatafakerRegistry CRUD operations
- Thread-safety (concurrent registration)
- TypeParser recognizes custom types
- DatafakerGenerator invokes custom functions
- Error handling (unregistered type)

### Integration Tests (10+ tests)
- End-to-end with custom types
- YAML registry loading
- CLI --registry flag
- Multiple custom types in single structure
- Locale support with custom types

### Performance Tests
- Registry lookup overhead (<1μs per lookup)
- No impact on built-in type performance
- Concurrent registration benchmarks

---

## Non-Goals (Out of Scope)

- ❌ Reflection-based provider discovery (too fragile)
- ❌ Hot-reload of registry during generation (restart required)
- ❌ Type validation at registration (fail at generation time)
- ❌ Custom type versioning or migration tools
- ❌ Graphical registry editor (CLI/YAML is sufficient)

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Performance regression | LOW | Medium | Benchmark registry lookup, cache negative results |
| Breaking changes to TypeParser | MEDIUM | High | Comprehensive tests, backward compatibility |
| User confusion (built-in vs custom) | MEDIUM | Low | Clear docs, list-types command |
| Thread-safety bugs | LOW | High | Extensive concurrency tests, use ConcurrentHashMap |

---

## Definition of Done

- [ ] DatafakerRegistry implemented with thread-safe operations
- [ ] CustomDatafakerType added to type system
- [ ] TypeParser checks registry for unknown types
- [ ] DatafakerGenerator supports custom types
- [ ] YAML registry parser (optional)
- [ ] CLI `--registry` flag
- [ ] CLI `list-types` command
- [ ] Unit tests >= 25, coverage >= 90%
- [ ] Integration tests >= 10
- [ ] Performance: Registry lookup <1μs
- [ ] README documentation with examples
- [ ] Example registry files (medical, entertainment, food)
- [ ] Code review approved
- [ ] Backward compatibility verified (all existing tests pass)

---

## Future Enhancements (Post-Plugin Architecture)

1. **Registry Marketplace**: Community-shared registry files on GitHub
2. **Type Aliases**: Shorthand names for common custom types
3. **Composite Custom Types**: Combine multiple providers into one type
4. **Validation Rules**: Optional schema for custom type outputs
5. **IDE Support**: Autocomplete for registered custom types
