# US-007: Primitive Data Generators

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: US-004, US-006

---

## User Story

As a **test data engineer**, I want **generators for primitive data types** so that **I can produce random values within specified ranges for basic fields like strings, numbers, dates, and booleans**.

---

## Acceptance Criteria

- ✅ CharGenerator produces alphanumeric strings with specified length ranges
- ✅ IntGenerator produces integers within min/max ranges
- ✅ DecimalGenerator produces BigDecimal values with specified scale
- ✅ BooleanGenerator produces random true/false values
- ✅ DateGenerator produces LocalDate values within date ranges
- ✅ TimestampGenerator produces Instant values within timestamp ranges
- ✅ EnumGenerator selects randomly from predefined values
- ✅ All generators implement DataTypeGenerator<T> interface
- ✅ All generators are thread-safe (stateless, use provided Random)
- ✅ Range boundaries are inclusive

---

## Implementation Notes

### Generator Interface
```java
public interface DataTypeGenerator<T> {
    T generate(Random random);
}
```

### Generator Implementations
- **CharGenerator**: Generate alphanumeric strings using character pool
- **IntGenerator**: Use `random.nextInt(max - min + 1) + min`
- **DecimalGenerator**: Generate double, convert to BigDecimal with rounding
- **BooleanGenerator**: Use `random.nextBoolean()`
- **DateGenerator**: Generate epoch day within range, convert to LocalDate
- **TimestampGenerator**: Generate epoch seconds within range, convert to Instant
- **EnumGenerator**: Select random index from values list

### Performance Considerations
- Generators are hot path - optimize for speed
- No object allocation in tight loops where possible
- Reuse character pools and other constants
- Use primitive operations when possible

---

## Testing Requirements

### Unit Tests
- Each generator produces values within specified ranges
- Boundary testing (min and max values generated)
- Distribution testing (values spread across range)
- Thread safety verification (same generator, multiple threads)
- Deterministic output with same seed

### Property-Based Tests
- Generate 10,000 values, verify all within range
- Verify no values outside boundaries
- Statistical distribution checks

### Test Coverage
- All generators tested individually
- Boundary conditions
- Range validation
- Thread safety

---

## Definition of Done

- [ ] DataTypeGenerator interface created
- [ ] All 7 primitive generators implemented
- [ ] All generators are stateless and thread-safe
- [ ] Unit tests for each generator
- [ ] Property-based tests for range compliance
- [ ] Test coverage >= 90%
- [ ] Performance is acceptable for hot paths
- [ ] Code follows project style guidelines
- [ ] PR reviewed and approved
