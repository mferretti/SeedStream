# US-003: Job Definition Schema Parser

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: US-002

---

## User Story

As a **QA engineer**, I want **to define generation jobs in YAML files** so that **I can configure data sources, seeds, and destination settings for different test scenarios**.

---

## Acceptance Criteria

- ✅ YAML files in `config/jobs/` directory define generation jobs
- ✅ Parser loads and validates job definitions
- ✅ Support for multiple seed types: embedded, file, env, remote
- ✅ Seed configuration validates required fields per type
- ✅ Optional `structures_path` for locating nested structure definitions
- ✅ Destination-specific configuration in `conf` section
- ✅ ParseException thrown with clear messages for invalid configurations
- ✅ Default seed (embedded, value 0) with warning if not specified

---

## Implementation Notes

### Model Classes
- **JobDefinition**: Top-level job with source, seed, destination, and configuration
- **SeedConfig**: Seed configuration supporting 4 types with type-specific validation

### Seed Types
1. **Embedded**: Value directly in YAML (requires `value` field)
2. **File**: Read from file (requires `path` field)
3. **Env**: Read from environment variable (requires `env_var` field)
4. **Remote**: Fetch from API (requires `url` field, optional `auth`)

### Parser Behavior
- Validates required fields per seed type
- Defaults to embedded seed with value 0 if not specified (with warning)
- Validates that `conf` section is present for destination configuration
- Maps generic `conf` map for destination-specific parsing later

### Example Job YAML
```yaml
source: address.yaml
seed:
  type: embedded
  value: 12345
destination: file
conf:
  path: /tmp/output/addresses
  compress: false
```

---

## Testing Requirements

### Unit Tests
- Parse valid job definitions for all seed types
- Validate embedded seed requires `value`
- Validate file seed requires `path`
- Validate env seed requires `env_var`
- Validate remote seed requires `url`
- Default to embedded seed 0 with warning when missing
- Reject job without `source`
- Reject job without `conf` section

### Test Coverage
- All seed types parsed correctly
- Type-specific field validation
- Missing required fields
- Default behavior
- Error messages clarity

---

## Definition of Done

- [ ] JobDefinition and SeedConfig model classes created
- [ ] JobDefinitionParser implementation complete
- [ ] Support for all 4 seed types
- [ ] Type-specific validation logic implemented
- [ ] Unit tests cover all seed types and validation rules
- [ ] Test coverage >= 90%
- [ ] Clear ParseException messages
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
