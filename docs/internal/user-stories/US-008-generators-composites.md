# US-008: Composite Data Generators

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: US-007

---

## User Story

As a **test data engineer**, I want **generators for composite data types** so that **I can create nested objects and arrays to represent complex real-world data structures**.

---

## Acceptance Criteria

- ✅ ObjectGenerator generates nested structures from DataStructure definitions
- ✅ ArrayGenerator generates variable-length arrays with typed elements
- ✅ StructureRegistry caches loaded structures to avoid repeated file reads
- ✅ ObjectGenerator applies field aliases to output
- ✅ DataGeneratorFactory creates appropriate generator for any type string
- ✅ Circular reference detection prevents infinite loops
- ✅ Support for deeply nested structures (objects within objects)
- ✅ Support for arrays of primitives and arrays of objects

---

## Implementation Notes

### StructureRegistry
- Loads DataStructure definitions from filesystem
- Caches loaded structures in ConcurrentHashMap
- Supports configurable structures path
- Provides manual registration for testing

### ObjectGenerator
- Loads structure definition from registry
- Iterates through field definitions
- Creates generator for each field using factory
- Generates value for each field
- Uses field alias for output key (or field name if no alias)
- Returns Map<String, Object>

### ArrayGenerator
- Takes element generator and min/max length
- Generates random length within bounds
- Generates N elements using element generator
- Returns List<Object>

### DataGeneratorFactory
- Parses type strings (e.g., "char[3..50]", "object[address]")
- Creates appropriate generator for each type
- Caches generator instances where possible
- Handles type parsing errors gracefully

### Circular Reference Detection
- Track structure dependency graph
- Detect cycles before generation starts
- Throw clear error message with cycle path

---

## Testing Requirements

### Unit Tests
- StructureRegistry loads and caches structures
- ObjectGenerator creates correct nested structure
- Field aliases applied correctly
- ArrayGenerator creates arrays of correct length
- Factory creates correct generator for each type
- Circular reference detection works

### Integration Tests
- Generate complex nested structures
- Verify output structure matches definition
- Test deep nesting (5+ levels)
- Test arrays of objects
- Test arrays of primitives

### Test Coverage
- All generator types
- Nested structures
- Arrays
- Alias handling
- Circular reference detection

---

## Definition of Done

- [ ] StructureRegistry implemented with caching
- [ ] ObjectGenerator generates nested structures
- [ ] ArrayGenerator generates variable-length arrays
- [ ] DataGeneratorFactory creates generators from type strings
- [ ] Circular reference detection implemented
- [ ] GeneratorException for errors
- [ ] Unit tests for all components
- [ ] Integration tests for complex structures
- [ ] Test coverage >= 90%
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
