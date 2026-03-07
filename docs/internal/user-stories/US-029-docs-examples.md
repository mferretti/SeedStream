# US-029: Example Configurations

**Status**: ✅ Complete  
**Priority**: P2 (Medium)  
**Phase**: 7 - Documentation  
**Dependencies**: US-028  
**Completion Date**: March 6, 2026

---

## User Story

As a **new user**, I want **comprehensive example configurations** so that **I can learn from real-world examples and quickly adapt them for my domain**.

---

## Acceptance Criteria

- ✅ At least 5 complete example domains
- ✅ Each domain has 3-5 related data structures
- ✅ Each structure has corresponding job files
- ✅ Domain-specific README explaining the example
- ✅ Examples cover common patterns (relationships, arrays, etc.)
- ✅ All examples are runnable out-of-the-box
- ✅ Examples demonstrate different destinations
- ✅ Examples demonstrate different geolocations

---

## Implementation Notes

### Example Domains
Located in `examples/` directory:

1. **E-commerce** (`examples/ecommerce/`)
   - user.yaml, product.yaml, order.yaml, review.yaml
   - Demonstrates: references, arrays, semantic types

2. **Financial** (`examples/financial/`)
   - account.yaml, transaction.yaml, customer.yaml, loan.yaml
   - Demonstrates: decimals, dates, enums

3. **IoT** (`examples/iot/`)
   - device.yaml, sensor_reading.yaml, alert.yaml
   - Demonstrates: timestamps, numeric ranges, arrays

4. **Social Media** (`examples/social/`)
   - user.yaml, post.yaml, comment.yaml, like.yaml
   - Demonstrates: references, nested objects

5. **Healthcare** (`examples/healthcare/`)
   - patient.yaml, appointment.yaml, prescription.yaml
   - Demonstrates: dates, complex nested structures, privacy

### Each Example Includes
- **structures/**: YAML structure definitions
- **jobs/**: Job definition files
- **README.md**: Domain explanation and usage
- **run.sh**: Script to execute examples

---

## Testing Requirements

### Example Validation
- Each example generates data successfully
- Output is domain-appropriate
- run.sh scripts work
- READMEs are clear and accurate

### Manual Review
- Examples look realistic
- Cover diverse patterns
- Easy to understand
- Easy to adapt

---

## Definition of Done

- [ ] 5 complete example domains created
- [ ] Each domain has 3-5 structures
- [ ] Each domain has job files
- [ ] Each domain has README
- [ ] Each domain has run script
- [ ] All examples tested and working
- [ ] Examples demonstrate key features
- [ ] Documentation links to examples
- [ ] PR reviewed and approved
