# TASK-015: Formats Module - Protobuf Serializer

**Status**: ⏸️ Not Started  
**Priority**: P3 (Low - Future Enhancement)  
**Phase**: 3 - Output Formats  
**Dependencies**: TASK-013 (JSON Serializer)  
**Human Supervision**: MEDIUM (requires schema generation)

---

## Objective

Implement Protobuf format serializer for high-performance binary serialization. Requires dynamic schema generation from data structure definitions.

---

## Background

Protocol Buffers (Protobuf) provides:
- Compact binary format (smaller than JSON)
- Fast serialization/deserialization
- Schema evolution support
- Wide language support

**Challenge**: Protobuf requires compile-time `.proto` schemas, but our data structures are dynamic YAML.

**Solution**: Use `protobuf-java-util` for dynamic message building or generate `.proto` files on-the-fly.

---

## Implementation Details

### Step 1: Add Dependencies

**File**: `formats/build.gradle.kts`

```kotlin
dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    
    // Add Protobuf dependencies
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.1")
}
```

---

### Step 2: Create ProtobufSerializer (Stub for Future)

**File**: `formats/src/main/java/com/datagenerator/formats/ProtobufSerializer.java`

```java
package com.datagenerator.formats;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Protobuf serializer (FUTURE IMPLEMENTATION).
 * 
 * Current status: Not implemented
 * Priority: P3 (Low)
 * 
 * Implementation approach:
 * 1. Generate .proto schema from DataStructure
 * 2. Compile schema at runtime using protoc
 * 3. Use DynamicMessage for serialization
 * 
 * Alternative: Use JSON → Protobuf conversion via protobuf-java-util
 */
@Slf4j
public class ProtobufSerializer implements FormatSerializer {
    
    public ProtobufSerializer() {
        throw new UnsupportedOperationException(
            "Protobuf serialization is not yet implemented (TASK-015 - P3 priority)");
    }
    
    @Override
    public byte[] serialize(Map<String, Object> record) {
        throw new UnsupportedOperationException("Not implemented");
    }
    
    @Override
    public String getFileExtension() {
        return "pb";
    }
}
```

---

## Future Implementation Steps

### Step 1: Generate .proto Schema

Convert DataStructure to .proto syntax:

```java
public class ProtoSchemaGenerator {
    
    public String generateSchema(DataStructure structure) {
        StringBuilder proto = new StringBuilder();
        proto.append("syntax = \"proto3\";\n\n");
        proto.append("message ").append(capitalize(structure.getName())).append(" {\n");
        
        int fieldNumber = 1;
        for (FieldDefinition field : structure.getFields()) {
            String protoType = mapToProtoType(field.getDatatype());
            proto.append("  ").append(protoType).append(" ")
                .append(field.getName()).append(" = ")
                .append(fieldNumber++).append(";\n");
        }
        
        proto.append("}\n");
        return proto.toString();
    }
    
    private String mapToProtoType(String datatype) {
        // Map data types to proto types
        // char → string
        // int → int32
        // decimal → double
        // boolean → bool
        // date/timestamp → string (ISO-8601)
        // object → nested message
        // array → repeated
    }
}
```

### Step 2: Compile Schema Dynamically

Use `protoc` programmatically or use dynamic messages.

### Step 3: Serialize Using DynamicMessage

```java
DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
// Populate fields from record map
return builder.build().toByteArray();
```

---

## Acceptance Criteria (When Implemented)

- ⏸️ Generates .proto schema from DataStructure
- ⏸️ Compiles schema at runtime
- ⏸️ Serializes records to binary Protobuf format
- ⏸️ Supports nested objects and arrays
- ⏸️ File extension: .pb
- ⏸️ All unit tests pass

---

## Alternative: JSON-Protobuf Hybrid

**Simpler approach** for initial implementation:
1. Serialize to JSON first (using JsonSerializer)
2. Convert JSON to Protobuf using `JsonFormat.parser()`
3. Requires pre-defined .proto schema (not dynamic)

**Trade-off**: Less flexible, but easier to implement

---

## Testing

```bash
# Will be added when implemented
./gradlew :formats:test
```

---

## Documentation

**Status Note**: Protobuf serialization is planned but not yet implemented due to complexity of dynamic schema generation. JSON and CSV formats cover most use cases.

**Priority Justification**: 
- P3 (Low): JSON/CSV sufficient for 95% of use cases
- Protobuf mainly beneficial for:
  - Very high throughput scenarios (millions of records/sec)
  - Cross-language systems requiring compact binary format
  - Systems already using Protobuf infrastructure

---

**Completion Date**: [Deferred - Low Priority]
