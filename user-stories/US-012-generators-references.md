# US-012: Reference Generator for Foreign Keys

**Status**: ⏸️ Not Started (Deferred to Phase 4)  
**Priority**: P2 (Medium)  
**Phase**: 4 - Advanced Features  
**Dependencies**: US-008

---

## User Story

As a **test data engineer**, I want **foreign key references between generated records** so that **I can create relational test data where orders reference users, line items reference orders, etc.**.

---

## Acceptance Criteria

- ✅ `ref[structure.field]` type syntax references previously generated records
- ✅ ReferenceCache stores generated values for later referencing
- ✅ Thread-safe cache with LRU eviction (configurable max size)
- ✅ ReferenceGenerator selects random value from cache
- ✅ Clear error if referenced structure not yet generated
- ✅ ObjectGenerator automatically caches field values
- ✅ Cache key format: `structure_name.field_name`
- ✅ Support for multi-level references (order → user, line_item → order)

---

## Implementation Notes

### Architecture
```
1. Generate user records → cache user.id values
2. Generate order records → reference cached user.id for order.user_id
3. Generate line_item records → reference cached order.id for line_item.order_id
```

### ReferenceCache
- ConcurrentHashMap<String, CopyOnWriteArrayList<Object>>
- Key format: "structure.field" (e.g., "user.id")
- Thread-safe read/write operations
- LRU eviction when cache exceeds max size per key
- Provides stats (cache size per key)

### ReferenceGenerator
- Constructor: targetStructure, targetField, cache
- generate(): Get random value from cache for key
- Throw GeneratorException if cache empty (referenced structure not generated yet)

### ObjectGenerator Enhancement
Add optional ReferenceCache parameter:
- After generating each field value, cache it
- Cache key: `structureName + "." + fieldName`
- Only cache if ReferenceCache provided

### Type Syntax
`ref[structure.field]` parsed as ReferenceType:
- structure: Target structure name
- field: Target field name

---

## Testing Requirements

### Unit Tests
- ReferenceCache stores and retrieves values
- LRU eviction works correctly
- Thread-safe concurrent access
- ReferenceGenerator gets random value from cache
- Error thrown when cache empty
- ObjectGenerator caches field values

### Integration Tests
- Generate users, then orders referencing users
- Generate multi-level references (user → order → line_item)
- Verify referenced IDs exist in parent records
- Test with large datasets (cache eviction scenarios)

### Edge Cases
- Reference to non-existent structure
- Reference before structure is generated
- Cache overflow scenarios

---

## Definition of Done

- [ ] ReferenceCache implemented with thread-safe operations
- [ ] ReferenceGenerator implemented
- [ ] ObjectGenerator integrated with ReferenceCache
- [ ] TypeParser extended to parse `ref[...]` syntax
- [ ] ReferenceType class created
- [ ] GeneratorException on empty cache
- [ ] Unit tests for cache, generator, and integration
- [ ] Integration tests with multi-level references
- [ ] Test coverage >= 90%
- [ ] Documentation with reference examples
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
