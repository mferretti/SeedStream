# TASK-002: Schema Module - Data Structure Parser

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-001 (Project Scaffolding)  
**Human Supervision**: LOW (straightforward YAML parsing)

---

## Objective

Implement YAML parser for data structure definitions that define schema, field types, ranges, aliases, and geolocation settings.

---

## Background

Data structures are defined in YAML files under `config/structures/`. Example:

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
  postal_code:
    datatype: postal_code
```

The parser must:
- Load and validate YAML structure
- Map to Java model classes
- Validate field definitions
- Support nested object references

---

## Implementation Details

### Step 1: Create Model Classes

**File**: `schema/src/main/java/com/datagenerator/schema/model/DataStructure.java`

```java
package com.datagenerator.schema.model;

import lombok.Value;
import java.util.List;
import java.util.Optional;

/**
 * Represents a data structure definition from YAML.
 */
@Value
public class DataStructure {
    String name;
    String geolocation;
    List<FieldDefinition> fields;
    
    /**
     * Get geolocation or default to "en-US".
     */
    public String getGeolocationOrDefault() {
        return Optional.ofNullable(geolocation).orElse("en-US");
    }
}
```

**File**: `schema/src/main/java/com/datagenerator/schema/model/FieldDefinition.java`

```java
package com.datagenerator.schema.model;

import lombok.Value;
import java.util.Optional;

/**
 * Represents a field definition within a data structure.
 */
@Value
public class FieldDefinition {
    String name;
    String datatype;
    String alias;
    
    /**
     * Get field name or alias for output.
     */
    public String getOutputName() {
        return Optional.ofNullable(alias).orElse(name);
    }
}
```

---

### Step 2: Create Parser Class

**File**: `schema/src/main/java/com/datagenerator/schema/DataStructureParser.java`

```java
package com.datagenerator.schema;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser for data structure YAML definitions.
 */
@Slf4j
public class DataStructureParser {
    
    private final ObjectMapper mapper;
    
    public DataStructureParser() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * Parse YAML content into DataStructure.
     * 
     * @param yaml YAML content as string
     * @return Parsed data structure
     * @throws ParseException if parsing fails
     */
    @SuppressWarnings("unchecked")
    public DataStructure parse(String yaml) {
        try {
            Map<String, Object> root = mapper.readValue(yaml, Map.class);
            
            String name = (String) root.get("name");
            if (name == null || name.isBlank()) {
                throw new ParseException("Structure name is required");
            }
            
            String geolocation = (String) root.get("geolocation");
            
            Map<String, Object> dataMap = (Map<String, Object>) root.get("data");
            if (dataMap == null || dataMap.isEmpty()) {
                throw new ParseException("Structure must have at least one field in 'data' section");
            }
            
            List<FieldDefinition> fields = parseFields(dataMap);
            
            return new DataStructure(name, geolocation, fields);
            
        } catch (Exception e) {
            log.error("Failed to parse data structure YAML", e);
            throw new ParseException("Failed to parse data structure: " + e.getMessage(), e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<FieldDefinition> parseFields(Map<String, Object> dataMap) {
        List<FieldDefinition> fields = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
            String fieldName = entry.getKey();
            Map<String, Object> fieldConfig = (Map<String, Object>) entry.getValue();
            
            String datatype = (String) fieldConfig.get("datatype");
            if (datatype == null || datatype.isBlank()) {
                throw new ParseException("Field '" + fieldName + "' must have 'datatype'");
            }
            
            String alias = (String) fieldConfig.get("alias");
            
            fields.add(new FieldDefinition(fieldName, datatype, alias));
        }
        
        return fields;
    }
}
```

---

### Step 3: Create Custom Exception

**File**: `schema/src/main/java/com/datagenerator/schema/ParseException.java`

```java
package com.datagenerator.schema;

/**
 * Exception thrown when YAML parsing fails.
 */
public class ParseException extends RuntimeException {
    
    public ParseException(String message) {
        super(message);
    }
    
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 4: Write Unit Tests

**File**: `schema/src/test/java/com/datagenerator/schema/DataStructureParserTest.java`

```java
package com.datagenerator.schema;

import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.FieldDefinition;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataStructureParserTest {
    
    private final DataStructureParser parser = new DataStructureParser();
    
    @Test
    void shouldParseValidStructure() {
        String yaml = """
            name: address
            geolocation: italy
            data:
              street:
                datatype: char[5..50]
                alias: "via"
              city:
                datatype: city
            """;
        
        DataStructure structure = parser.parse(yaml);
        
        assertThat(structure.getName()).isEqualTo("address");
        assertThat(structure.getGeolocation()).isEqualTo("italy");
        assertThat(structure.getFields()).hasSize(2);
        
        FieldDefinition street = structure.getFields().get(0);
        assertThat(street.getName()).isEqualTo("street");
        assertThat(street.getDatatype()).isEqualTo("char[5..50]");
        assertThat(street.getAlias()).isEqualTo("via");
        assertThat(street.getOutputName()).isEqualTo("via");
        
        FieldDefinition city = structure.getFields().get(1);
        assertThat(city.getName()).isEqualTo("city");
        assertThat(city.getAlias()).isNull();
        assertThat(city.getOutputName()).isEqualTo("city");
    }
    
    @Test
    void shouldDefaultGeolocationToEnUS() {
        String yaml = """
            name: test
            data:
              field1:
                datatype: char[1..10]
            """;
        
        DataStructure structure = parser.parse(yaml);
        assertThat(structure.getGeolocationOrDefault()).isEqualTo("en-US");
    }
    
    @Test
    void shouldThrowExceptionWhenNameMissing() {
        String yaml = """
            data:
              field1:
                datatype: char[1..10]
            """;
        
        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("name is required");
    }
    
    @Test
    void shouldThrowExceptionWhenDataMissing() {
        String yaml = """
            name: test
            """;
        
        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("at least one field");
    }
    
    @Test
    void shouldThrowExceptionWhenDatatypeMissing() {
        String yaml = """
            name: test
            data:
              field1:
                alias: "f1"
            """;
        
        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("must have 'datatype'");
    }
}
```

---

## Acceptance Criteria

- ✅ Parser loads valid YAML structure definitions
- ✅ Validates required fields (name, data, datatype per field)
- ✅ Supports optional geolocation and alias fields
- ✅ Throws clear exceptions for invalid YAML
- ✅ All unit tests pass
- ✅ Code formatted with Spotless

---

## Testing

Run tests:
```bash
./gradlew :schema:test
```

Expected output:
```
DataStructureParserTest > shouldParseValidStructure() PASSED
DataStructureParserTest > shouldDefaultGeolocationToEnUS() PASSED
DataStructureParserTest > shouldThrowExceptionWhenNameMissing() PASSED
DataStructureParserTest > shouldThrowExceptionWhenDataMissing() PASSED
DataStructureParserTest > shouldThrowExceptionWhenDatatypeMissing() PASSED
```

---

**Completion Date**: [Mark when complete]
