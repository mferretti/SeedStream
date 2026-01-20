# TASK-003: Schema Module - Job Definition Parser

**Status**: ✅ Complete  
**Priority**: P0 (Critical)  
**Phase**: 1 - Core Foundation  
**Dependencies**: TASK-002 (Data Structure Parser)  
**Human Supervision**: LOW (straightforward YAML parsing)

---

## Objective

Implement YAML parser for job definition files that specify data source, seed configuration, and destination-specific settings.

---

## Background

Job definitions reference data structures and configure generation parameters. Example:

```yaml
source: address.yaml
seed:
  type: embedded
  value: 12345
destination: file
conf:
  path: /tmp/output/addresses
  compress: false
  append: false
```

The parser must:
- Load and validate job YAML
- Support multiple seed types (embedded, file, env, remote)
- Parse destination-specific configuration
- Validate required fields

---

## Implementation Details

### Step 1: Create Model Classes

**File**: `schema/src/main/java/com/datagenerator/schema/model/JobDefinition.java`

```java
package com.datagenerator.schema.model;

import lombok.Value;
import java.util.Map;

/**
 * Represents a job definition from YAML.
 */
@Value
public class JobDefinition {
    String source;
    String structuresPath;
    SeedConfig seed;
    String destination;
    Map<String, Object> conf;
}
```

**File**: `schema/src/main/java/com/datagenerator/schema/model/SeedConfig.java`

```java
package com.datagenerator.schema.model;

import lombok.Value;
import java.util.Map;

/**
 * Seed configuration supporting multiple source types.
 */
@Value
public class SeedConfig {
    SeedType type;
    Long value;
    String path;
    String envVar;
    String url;
    Map<String, String> auth;
    
    public enum SeedType {
        EMBEDDED, FILE, ENV, REMOTE
    }
}
```

---

### Step 2: Create Parser Class

**File**: `schema/src/main/java/com/datagenerator/schema/JobDefinitionParser.java`

```java
package com.datagenerator.schema;

import com.datagenerator.schema.model.JobDefinition;
import com.datagenerator.schema.model.SeedConfig;
import com.datagenerator.schema.model.SeedConfig.SeedType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

/**
 * Parser for job definition YAML files.
 */
@Slf4j
public class JobDefinitionParser {
    
    private final ObjectMapper mapper;
    
    public JobDefinitionParser() {
        this.mapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * Parse YAML content into JobDefinition.
     * 
     * @param yaml YAML content as string
     * @return Parsed job definition
     * @throws ParseException if parsing fails
     */
    @SuppressWarnings("unchecked")
    public JobDefinition parse(String yaml) {
        try {
            Map<String, Object> root = mapper.readValue(yaml, Map.class);
            
            String source = (String) root.get("source");
            if (source == null || source.isBlank()) {
                throw new ParseException("Job must specify 'source' data structure");
            }
            
            String structuresPath = (String) root.get("structures_path");
            
            SeedConfig seedConfig = parseSeedConfig((Map<String, Object>) root.get("seed"));
            
            String destination = (String) root.get("destination");
            
            Map<String, Object> conf = (Map<String, Object>) root.get("conf");
            if (conf == null || conf.isEmpty()) {
                throw new ParseException("Job must specify 'conf' section for destination configuration");
            }
            
            return new JobDefinition(source, structuresPath, seedConfig, destination, conf);
            
        } catch (Exception e) {
            log.error("Failed to parse job definition YAML", e);
            throw new ParseException("Failed to parse job definition: " + e.getMessage(), e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private SeedConfig parseSeedConfig(Map<String, Object> seedMap) {
        if (seedMap == null) {
            // Default to embedded seed of 0 with warning
            log.warn("No seed configuration found, defaulting to embedded value 0");
            return new SeedConfig(SeedType.EMBEDDED, 0L, null, null, null, null);
        }
        
        String typeStr = (String) seedMap.get("type");
        if (typeStr == null) {
            throw new ParseException("Seed configuration must specify 'type'");
        }
        
        SeedType type = switch (typeStr.toLowerCase()) {
            case "embedded" -> SeedType.EMBEDDED;
            case "file" -> SeedType.FILE;
            case "env" -> SeedType.ENV;
            case "remote" -> SeedType.REMOTE;
            default -> throw new ParseException("Unknown seed type: " + typeStr);
        };
        
        Long value = seedMap.get("value") != null ? ((Number) seedMap.get("value")).longValue() : null;
        String path = (String) seedMap.get("path");
        String envVar = (String) seedMap.get("env_var");
        String url = (String) seedMap.get("url");
        Map<String, String> auth = (Map<String, String>) seedMap.get("auth");
        
        // Validate type-specific required fields
        switch (type) {
            case EMBEDDED -> {
                if (value == null) {
                    throw new ParseException("Embedded seed must specify 'value'");
                }
            }
            case FILE -> {
                if (path == null) {
                    throw new ParseException("File seed must specify 'path'");
                }
            }
            case ENV -> {
                if (envVar == null) {
                    throw new ParseException("Env seed must specify 'env_var'");
                }
            }
            case REMOTE -> {
                if (url == null) {
                    throw new ParseException("Remote seed must specify 'url'");
                }
            }
        }
        
        return new SeedConfig(type, value, path, envVar, url, auth);
    }
}
```

---

### Step 3: Write Unit Tests

**File**: `schema/src/test/java/com/datagenerator/schema/JobDefinitionParserTest.java`

```java
package com.datagenerator.schema;

import com.datagenerator.schema.model.JobDefinition;
import com.datagenerator.schema.model.SeedConfig.SeedType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JobDefinitionParserTest {
    
    private final JobDefinitionParser parser = new JobDefinitionParser();
    
    @Test
    void shouldParseJobWithEmbeddedSeed() {
        String yaml = """
            source: address.yaml
            seed:
              type: embedded
              value: 12345
            destination: file
            conf:
              path: /tmp/addresses
            """;
        
        JobDefinition job = parser.parse(yaml);
        
        assertThat(job.getSource()).isEqualTo("address.yaml");
        assertThat(job.getSeed().getType()).isEqualTo(SeedType.EMBEDDED);
        assertThat(job.getSeed().getValue()).isEqualTo(12345L);
        assertThat(job.getDestination()).isEqualTo("file");
        assertThat(job.getConf()).containsKey("path");
    }
    
    @Test
    void shouldParseJobWithFileSeed() {
        String yaml = """
            source: user.yaml
            seed:
              type: file
              path: /secrets/seed.txt
            conf:
              bootstrap: localhost:9092
            """;
        
        JobDefinition job = parser.parse(yaml);
        
        assertThat(job.getSeed().getType()).isEqualTo(SeedType.FILE);
        assertThat(job.getSeed().getPath()).isEqualTo("/secrets/seed.txt");
    }
    
    @Test
    void shouldParseJobWithEnvSeed() {
        String yaml = """
            source: invoice.yaml
            seed:
              type: env
              env_var: GENERATION_SEED
            conf:
              db_url: jdbc:postgresql://localhost/test
            """;
        
        JobDefinition job = parser.parse(yaml);
        
        assertThat(job.getSeed().getType()).isEqualTo(SeedType.ENV);
        assertThat(job.getSeed().getEnvVar()).isEqualTo("GENERATION_SEED");
    }
    
    @Test
    void shouldParseJobWithRemoteSeed() {
        String yaml = """
            source: company.yaml
            seed:
              type: remote
              url: https://api.example.com/seed
              auth:
                type: bearer
                token: secret123
            conf:
              topic: companies
            """;
        
        JobDefinition job = parser.parse(yaml);
        
        assertThat(job.getSeed().getType()).isEqualTo(SeedType.REMOTE);
        assertThat(job.getSeed().getUrl()).isEqualTo("https://api.example.com/seed");
        assertThat(job.getSeed().getAuth()).containsEntry("type", "bearer");
    }
    
    @Test
    void shouldDefaultToSeedZeroWhenMissing() {
        String yaml = """
            source: test.yaml
            conf:
              path: /tmp/test
            """;
        
        JobDefinition job = parser.parse(yaml);
        
        assertThat(job.getSeed().getType()).isEqualTo(SeedType.EMBEDDED);
        assertThat(job.getSeed().getValue()).isEqualTo(0L);
    }
    
    @Test
    void shouldThrowExceptionWhenSourceMissing() {
        String yaml = """
            seed:
              type: embedded
              value: 123
            conf:
              path: /tmp/test
            """;
        
        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("source");
    }
    
    @Test
    void shouldThrowExceptionWhenConfMissing() {
        String yaml = """
            source: test.yaml
            seed:
              type: embedded
              value: 123
            """;
        
        assertThatThrownBy(() -> parser.parse(yaml))
            .isInstanceOf(ParseException.class)
            .hasMessageContaining("conf");
    }
}
```

---

## Acceptance Criteria

- ✅ Parser loads valid job definition YAML
- ✅ Supports all seed types (embedded, file, env, remote)
- ✅ Validates required fields per seed type
- ✅ Defaults to seed 0 when missing (with warning)
- ✅ Parses destination configuration map
- ✅ All unit tests pass
- ✅ Code formatted with Spotless

---

## Testing

Run tests:
```bash
./gradlew :schema:test
```

---

**Completion Date**: [Mark when complete]
