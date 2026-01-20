# US-002: Data Structure Schema Parser

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: US-001

---

## User Story

As a **data engineer**, I want **to define data structures in YAML files** so that **I can specify schemas for generated test data with field types, ranges, aliases, and locale settings**.

---

## Acceptance Criteria

- ✅ YAML files in `config/structures/` directory define data structures
- ✅ Parser loads and validates YAML structure definitions
- ✅ Support for field definitions with datatype, alias, and geolocation
- ✅ Field aliases map internal names to output names (e.g., "nome" for "name")
- ✅ Geolocation defaults to "en-US" if not specified
- ✅ Validation ensures required fields (name, data section with at least one field)
- ✅ ParseException thrown with clear messages on invalid YAML
- ✅ Model classes are immutable (using Lombok @Value)

---

## Implementation Notes

### Model Classes
Create immutable model classes to represent parsed structures:
- **DataStructure**: Top-level structure with name, geolocation, and fields
- **FieldDefinition**: Individual field with name, datatype, and optional alias

### Parser Responsibilities
- Load YAML using Jackson YAML parser
- Validate required fields are present
- Map YAML structure to Java model objects
- Provide clear error messages for malformed YAML

### Example YAML Structure
```yaml
name: address
geolocation: italy
data:
  street:
    datatype: char[5..50]
    alias: "via"
  city:
    datatype: city
    alias: "citta"
```

---

## Testing Requirements

### Unit Tests
- Parse valid YAML structures successfully
- Reject structures without name
- Reject structures without data section
- Apply default geolocation when not specified
- Handle field aliases correctly
- Throw ParseException with clear messages for invalid YAML

### Test Coverage
- Valid structure parsing
- Missing required fields
- Empty data section
- Invalid YAML syntax
- Alias functionality

---

## Definition of Done

- [ ] DataStructure and FieldDefinition model classes created
- [ ] DataStructureParser implementation complete
- [ ] ParseException class created with clear error messages
- [ ] Unit tests cover all acceptance criteria
- [ ] Test coverage >= 90%
- [ ] Code follows project style guidelines
- [ ] No wildcard imports
- [ ] PR reviewed and approved
