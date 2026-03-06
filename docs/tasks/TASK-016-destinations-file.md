# TASK-016: Destinations Module - File Adapter

**Status**: ✅ Completed  
**Priority**: P1 (High)  
**Phase**: 4 - Destinations  
**Dependencies**: TASK-013 (JSON Serializer), TASK-014 (CSV Serializer)  
**Human Supervision**: LOW (straightforward file I/O)  
**Completed**: February 21, 2026  
**Implementation**: `destinations/src/main/java/com/datagenerator/destinations/file/FileDestination.java`  
**Tests**: 16 unit tests passing

---

## Completion Summary

**What Was Implemented:**
- ✅ DestinationAdapter interface created
- ✅ FileDestination with Java NIO
- ✅ BufferedWriter for efficient I/O
- ✅ Gzip compression support
- ✅ Append mode for incremental generation
- ✅ Auto-create parent directories
- ✅ Configurable buffer sizes (default 64KB)
- ✅ Proper resource cleanup (AutoCloseable)
- ✅ DestinationException for error handling
- ✅ FileDestinationConfig with Lombok @Builder

**Test Coverage:**
- 16 unit tests covering all features
- Write operations and lifecycle
- Compression (gzip)
- Append mode
- Buffer flushing
- Error handling
- Multiple format integration (JSON, CSV)

---

## Objective

Implement file destination adapter that writes generated records to filesystem using Java NIO for high performance. Support multiple formats (JSON, CSV), optional compression (gzip), and append mode.

---

## Background

Generated records need to be written to files for:
- Local testing and development
- CI/CD pipeline test data generation
- Batch processing input files
- Data warehouse bulk loads

**Requirements**:
- Fast I/O (Java NIO FileChannel, not BufferedWriter)
- Streaming (write records as generated, no buffering)
- Atomic writes (temp file + rename for crash safety)
- Optional compression (gzip for space savings)
- Append mode (incremental generation)

---

## Implementation Details

### Step 1: Create DestinationAdapter Interface

**File**: `destinations/src/main/java/com/datagenerator/destinations/DestinationAdapter.java`

```java
package com.datagenerator.destinations;

import java.io.Closeable;

/**
 * Interface for writing generated records to destinations (file, Kafka, database).
 */
public interface DestinationAdapter extends AutoCloseable {
    
    /**
     * Write a serialized record to the destination.
     * 
     * @param record Serialized record bytes (format-specific)
     * @throws DestinationException if write fails
     */
    void write(byte[] record);
    
    /**
     * Flush any pending writes to destination.
     * Should be called after batch of records or before close.
     * 
     * @throws DestinationException if flush fails
     */
    void flush();
    
    /**
     * Close the destination and release resources.
     * Automatically flushes pending writes.
     * 
     * @throws Exception if close fails
     */
    @Override
    void close() throws Exception;
}
```

---

### Step 2: Create FileDestination Implementation

**File**: `destinations/src/main/java/com/datagenerator/destinations/FileDestination.java`

**Implementation**:
```java
package com.datagenerator.destinations;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * File destination adapter using Java NIO for high-performance writes.
 * Supports optional gzip compression and append mode.
 */
@Slf4j
public class FileDestination implements DestinationAdapter {
    
    private final Path outputPath;
    private final boolean compress;
    private final boolean append;
    private final FileChannel channel;
    private final GZIPOutputStream gzipStream; // Null if not compressed
    
    /**
     * Create file destination.
     * 
     * @param basePath Base output path (without extension)
     * @param format Output format (for file extension)
     * @param compress Whether to gzip compress output
     * @param append Whether to append to existing file
     * @throws IOException if file creation fails
     */
    public FileDestination(String basePath, String format, boolean compress, boolean append) throws IOException {
        this.compress = compress;
        this.append = append;
        
        // Construct full path with extension
        String extension = format + (compress ? ".gz" : "");
        this.outputPath = Paths.get(basePath + "." + extension);
        
        // Create parent directories if needed
        Path parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }
        
        // Open file channel
        if (compress) {
            // For compressed output, use GZIPOutputStream
            var fileStream = append 
                ? Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                : Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            this.gzipStream = new GZIPOutputStream(fileStream);
            this.channel = null;
        } else {
            // For uncompressed output, use FileChannel (faster)
            this.channel = append
                ? FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                : FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            this.gzipStream = null;
        }
        
        log.info("Opened file destination: {} (compress={}, append={})", outputPath, compress, append);
    }
    
    @Override
    public void write(byte[] record) {
        try {
            if (compress) {
                // Write through GZIP stream
                gzipStream.write(record);
            } else {
                // Write through FileChannel (faster for uncompressed)
                ByteBuffer buffer = ByteBuffer.wrap(record);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
        } catch (IOException e) {
            throw new DestinationException("Failed to write to file: " + outputPath, e);
        }
    }
    
    @Override
    public void flush() {
        try {
            if (compress) {
                gzipStream.flush();
            } else {
                channel.force(false); // Flush to disk (false = don't sync metadata)
            }
        } catch (IOException e) {
            throw new DestinationException("Failed to flush file: " + outputPath, e);
        }
    }
    
    @Override
    public void close() throws Exception {
        try {
            flush(); // Flush pending writes
            
            if (compress) {
                gzipStream.close();
            } else {
                channel.close();
            }
            
            log.info("Closed file destination: {}", outputPath);
        } catch (IOException e) {
            throw new DestinationException("Failed to close file: " + outputPath, e);
        }
    }
    
    /**
     * Get the output file path.
     * 
     * @return Path to output file
     */
    public Path getOutputPath() {
        return outputPath;
    }
}
```

---

### Step 3: Create DestinationException

**File**: `destinations/src/main/java/com/datagenerator/destinations/DestinationException.java`

```java
package com.datagenerator.destinations;

/**
 * Exception thrown when destination operations fail.
 */
public class DestinationException extends RuntimeException {
    
    public DestinationException(String message) {
        super(message);
    }
    
    public DestinationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 4: Implement Atomic Writes (Optional Enhancement)

**For crash safety**, write to temp file then rename:

**Enhanced FileDestination** (optional):
```java
public class FileDestination implements DestinationAdapter {
    
    private final Path finalPath;
    private final Path tempPath;
    private final boolean atomic;
    // ... other fields
    
    public FileDestination(String basePath, String format, boolean compress, boolean append, boolean atomic) throws IOException {
        this.atomic = atomic && !append; // Atomic writes only for non-append mode
        
        String extension = format + (compress ? ".gz" : "");
        this.finalPath = Paths.get(basePath + "." + extension);
        
        if (this.atomic) {
            // Write to temp file first
            this.tempPath = Paths.get(basePath + ".tmp." + extension);
            this.outputPath = tempPath;
        } else {
            this.tempPath = null;
            this.outputPath = finalPath;
        }
        
        // ... rest of initialization
    }
    
    @Override
    public void close() throws Exception {
        try {
            flush();
            
            if (compress) {
                gzipStream.close();
            } else {
                channel.close();
            }
            
            // Atomic rename
            if (atomic) {
                Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
                log.info("Atomically moved {} to {}", tempPath, finalPath);
            }
            
            log.info("Closed file destination: {}", finalPath);
        } catch (IOException e) {
            throw new DestinationException("Failed to close file: " + finalPath, e);
        }
    }
}
```

**For this task**, implement basic version WITHOUT atomic writes (simpler). Atomic writes can be added in future enhancement.

---

### Step 5: Create FileDestinationConfig

**File**: `schema/src/main/java/com/datagenerator/schema/config/FileDestinationConfig.java`

**Purpose**: Parse file destination configuration from YAML.

```java
package com.datagenerator.schema.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

/**
 * Configuration for file destination.
 */
@Value
@Builder
@Jacksonized
public class FileDestinationConfig {
    
    /**
     * Base output path (without extension).
     * Example: "output/addresses" → "output/addresses.json" or "output/addresses.json.gz"
     */
    @NotBlank(message = "File path is required")
    @JsonProperty("path")
    String path;
    
    /**
     * Optional compression type (none, gzip).
     * Default: none
     */
    @JsonProperty("compression")
    @Builder.Default
    String compression = "none";
    
    /**
     * Whether to append to existing file (vs overwrite).
     * Default: false (overwrite)
     */
    @JsonProperty("append")
    @Builder.Default
    boolean append = false;
}
```

**YAML example**:
```yaml
type: file
conf:
  path: output/addresses
  compression: gzip    # Optional
  append: false        # Optional
```

---

### Step 6: Integrate with JobDefinitionParser

**File**: `schema/src/main/java/com/datagenerator/schema/JobDefinitionParser.java`

**Add file destination config parsing**:
```java
public JobDefinition parse(String yaml) throws IOException {
    // ... existing parsing logic
    
    // Parse destination config based on type
    Object destinationConfig = switch (rawConfig.getType()) {
        case "kafka" -> objectMapper.convertValue(rawConfig.getConf(), KafkaDestinationConfig.class);
        case "file" -> objectMapper.convertValue(rawConfig.getConf(), FileDestinationConfig.class);
        case "database" -> objectMapper.convertValue(rawConfig.getConf(), DatabaseDestinationConfig.class);
        default -> throw new IllegalArgumentException("Unknown destination type: " + rawConfig.getType());
    };
    
    // ... rest of parsing
}
```

---

## Acceptance Criteria

- ✅ DestinationAdapter interface created with clear contract
- ✅ FileDestination implements DestinationAdapter
- ✅ Uses Java NIO FileChannel for uncompressed writes (high performance)
- ✅ Uses GZIPOutputStream for compressed writes
- ✅ Creates parent directories if needed
- ✅ Supports append mode (append to existing file)
- ✅ Supports overwrite mode (truncate existing file)
- ✅ Flushes writes on flush() call
- ✅ Closes channel/stream on close() call
- ✅ File extension includes format + compression (e.g., "json.gz")
- ✅ DestinationException thrown on failure with descriptive message
- ✅ FileDestinationConfig parses YAML configuration

---

## Testing Requirements

### Unit Tests

**File**: `destinations/src/test/java/com/datagenerator/destinations/FileDestinationTest.java`

**Test cases**:

1. **Test Uncompressed Write**:
```java
@Test
@TempDir
Path tempDir;

void shouldWriteUncompressedFile() throws Exception {
    String basePath = tempDir.resolve("output").toString();
    
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        String record1 = "{\"name\":\"Mario\"}\n";
        String record2 = "{\"name\":\"Luigi\"}\n";
        
        destination.write(record1.getBytes(StandardCharsets.UTF_8));
        destination.write(record2.getBytes(StandardCharsets.UTF_8));
        destination.flush();
    }
    
    // Verify file content
    Path outputFile = tempDir.resolve("output.json");
    assertThat(outputFile).exists();
    
    String content = Files.readString(outputFile);
    assertThat(content).isEqualTo("{\"name\":\"Mario\"}\n{\"name\":\"Luigi\"}\n");
}
```

2. **Test Compressed Write**:
```java
@Test
void shouldWriteCompressedFile(@TempDir Path tempDir) throws Exception {
    String basePath = tempDir.resolve("output").toString();
    
    try (FileDestination destination = new FileDestination(basePath, "json", true, false)) {
        String record = "{\"name\":\"Mario\"}\n";
        destination.write(record.getBytes(StandardCharsets.UTF_8));
        destination.flush();
    }
    
    // Verify file exists and is compressed
    Path outputFile = tempDir.resolve("output.json.gz");
    assertThat(outputFile).exists();
    
    // Decompress and verify content
    try (var gzipStream = new GZIPInputStream(Files.newInputStream(outputFile))) {
        String content = new String(gzipStream.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("{\"name\":\"Mario\"}\n");
    }
}
```

3. **Test Append Mode**:
```java
@Test
void shouldAppendToExistingFile(@TempDir Path tempDir) throws Exception {
    String basePath = tempDir.resolve("output").toString();
    
    // First write
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        destination.write("{\"name\":\"Mario\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    
    // Second write (append mode)
    try (FileDestination destination = new FileDestination(basePath, "json", false, true)) {
        destination.write("{\"name\":\"Luigi\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    
    // Verify both records present
    Path outputFile = tempDir.resolve("output.json");
    String content = Files.readString(outputFile);
    assertThat(content).isEqualTo("{\"name\":\"Mario\"}\n{\"name\":\"Luigi\"}\n");
}
```

4. **Test Overwrite Mode**:
```java
@Test
void shouldOverwriteExistingFile(@TempDir Path tempDir) throws Exception {
    String basePath = tempDir.resolve("output").toString();
    
    // First write
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        destination.write("{\"name\":\"Mario\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    
    // Second write (overwrite mode - append=false)
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        destination.write("{\"name\":\"Luigi\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    
    // Verify only second record present
    Path outputFile = tempDir.resolve("output.json");
    String content = Files.readString(outputFile);
    assertThat(content).isEqualTo("{\"name\":\"Luigi\"}\n");
}
```

5. **Test Parent Directory Creation**:
```java
@Test
void shouldCreateParentDirectories(@TempDir Path tempDir) throws Exception {
    String basePath = tempDir.resolve("subdir1/subdir2/output").toString();
    
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        destination.write("{\"name\":\"Mario\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    
    Path outputFile = tempDir.resolve("subdir1/subdir2/output.json");
    assertThat(outputFile).exists();
}
```

6. **Test Large File Write** (performance):
```java
@Test
void shouldWriteLargeFileEfficiently(@TempDir Path tempDir) throws Exception {
    String basePath = tempDir.resolve("large").toString();
    
    long startTime = System.currentTimeMillis();
    
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        // Write 100,000 records
        for (int i = 0; i < 100_000; i++) {
            String record = String.format("{\"id\":%d,\"name\":\"User%d\"}\n", i, i);
            destination.write(record.getBytes(StandardCharsets.UTF_8));
        }
        destination.flush();
    }
    
    long elapsedMs = System.currentTimeMillis() - startTime;
    
    // Should write 100k records in < 1 second
    assertThat(elapsedMs).isLessThan(1000);
    
    // Verify file size
    Path outputFile = tempDir.resolve("large.json");
    long fileSize = Files.size(outputFile);
    assertThat(fileSize).isGreaterThan(0);
}
```

7. **Test Error Handling**:
```java
@Test
void shouldThrowExceptionForInvalidPath() {
    // Invalid path (cannot write to directory)
    assertThatThrownBy(() -> {
        new FileDestination("/invalid/path/that/does/not/exist/and/cannot/be/created", "json", false, false);
    }).isInstanceOf(IOException.class);
}
```

**Minimum**: 7 unit tests (as above)

---

### Integration Tests

**File**: `destinations/src/test/java/com/datagenerator/destinations/FileDestinationIntegrationTest.java`

**Test end-to-end** with serializers:

```java
@Test
void shouldWriteJsonRecordsToFile(@TempDir Path tempDir) throws Exception {
    // Given: JSON serializer and file destination
    JsonSerializer serializer = new JsonSerializer();
    String basePath = tempDir.resolve("output").toString();
    
    try (FileDestination destination = new FileDestination(basePath, "json", false, false)) {
        // When: Generate and write records
        for (int i = 0; i < 10; i++) {
            Map<String, Object> record = Map.of("id", i, "name", "User" + i);
            byte[] bytes = serializer.serialize(record);
            destination.write(bytes);
        }
    }
    
    // Then: Verify file content
    Path outputFile = tempDir.resolve("output.json");
    List<String> lines = Files.readAllLines(outputFile);
    assertThat(lines).hasSize(10);
    assertThat(lines.get(0)).contains("\"id\":0");
    assertThat(lines.get(9)).contains("\"id\":9");
}
```

---

## Files Created

- `destinations/src/main/java/com/datagenerator/destinations/DestinationAdapter.java`
- `destinations/src/main/java/com/datagenerator/destinations/FileDestination.java`
- `destinations/src/main/java/com/datagenerator/destinations/DestinationException.java`
- `schema/src/main/java/com/datagenerator/schema/config/FileDestinationConfig.java`
- `destinations/src/test/java/com/datagenerator/destinations/FileDestinationTest.java`
- `destinations/src/test/java/com/datagenerator/destinations/FileDestinationIntegrationTest.java`

---

## Files Modified

- `schema/src/main/java/com/datagenerator/schema/JobDefinitionParser.java` (add file config parsing)

---

## Common Issues & Solutions

**Issue**: File not created  
**Solution**: Check parent directory exists or enable directory creation in code

**Issue**: Permission denied  
**Solution**: Check file path permissions, ensure writable directory

**Issue**: Slow writes  
**Solution**: Use FileChannel (not BufferedWriter) for uncompressed writes

**Issue**: Corrupted gzip file  
**Solution**: Ensure GZIPOutputStream is properly closed (calls finish())

**Issue**: File exists error in append mode  
**Solution**: Use StandardOpenOption.APPEND when opening file channel

---

## Completion Checklist

- [ ] DestinationAdapter interface created
- [ ] FileDestination implementation complete
- [ ] DestinationException created
- [ ] FileDestinationConfig created and integrated
- [ ] Parent directory creation implemented
- [ ] Compression support (gzip) implemented
- [ ] Append mode implemented
- [ ] Unit tests pass (7 tests)
- [ ] Integration test passes
- [ ] Build succeeds: `./gradlew :destinations:build`
- [ ] All tests pass: `./gradlew :destinations:test`
- [ ] Code formatted: `./gradlew :destinations:spotlessApply`
- [ ] Large file performance verified (100k records < 1 second)

---

**Estimated Effort**: 4-5 hours  
**Complexity**: Medium (file I/O and compression handling)
