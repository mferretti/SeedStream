/*
 * Copyright 2026 Marco Ferretti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datagenerator.destinations.file;

import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.FormatSerializer;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes generated records to files using Java NIO for high performance.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Java NIO for fast I/O
 *   <li>Buffered writes (configurable buffer size)
 *   <li>Batch writes (configurable batch size for 2-3x performance improvement)
 *   <li>Optional gzip compression
 *   <li>Append mode support
 *   <li>CSV header row (for CSV format)
 *   <li>Automatic parent directory creation
 * </ul>
 *
 * <p><b>Format Support:</b> JSON (newline-delimited), CSV (with headers), or any custom {@link
 * FormatSerializer}.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * FileDestinationConfig config = FileDestinationConfig.builder()
 *     .filePath(Paths.get("output/data.json"))
 *     .compress(true)
 *     .build();
 *
 * try (FileDestination dest = new FileDestination(config, new JsonSerializer())) {
 *     dest.open();
 *     dest.write(record1);
 *     dest.write(record2);
 *     dest.flush();
 * }
 * </pre>
 *
 * <p><b>Thread Safety:</b> Not thread-safe. Each writer should use its own instance.
 */
@Slf4j
public class FileDestination implements DestinationAdapter {
  private final FileDestinationConfig config;
  private final FormatSerializer serializer;

  private BufferedWriter writer;
  private boolean isOpen = false;
  private boolean headerWritten = false;

  // Batch writing optimization
  private final List<String> batchBuffer;
  private final StringBuilder batchBuilder;
  private final int estimatedRecordSize = 300; // Average JSON record size in bytes

  /**
   * Create file destination with configuration and serializer.
   *
   * @param config file output configuration
   * @param serializer format serializer (JSON, CSV, etc.)
   */
  public FileDestination(FileDestinationConfig config, FormatSerializer serializer) {
    this.config = config;
    this.serializer = serializer;
    this.batchBuffer = new ArrayList<>(config.getBatchSize());
    this.batchBuilder = new StringBuilder(config.getBatchSize() * estimatedRecordSize);
  }

  @Override
  public void open() {
    if (isOpen) {
      log.warn("File destination already open: {}", config.getFilePath());
      return;
    }

    try {
      Path filePath = config.getFilePath();

      // Create parent directories if they don't exist
      Path parentDir = filePath.getParent();
      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
        log.debug("Created parent directories: {}", parentDir);
      }

      // Add .gz extension if compression enabled
      if (config.isCompress() && !filePath.toString().endsWith(".gz")) {
        filePath = Path.of(filePath.toString() + ".gz");
      }

      // Open file for writing
      StandardOpenOption[] openOptions =
          config.isAppend()
              ? new StandardOpenOption[] {
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE
              }
              : new StandardOpenOption[] {
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
              };

      if (config.isCompress()) {
        // Gzip compressed output
        writer =
            new BufferedWriter(
                new OutputStreamWriter(
                    new GZIPOutputStream(Files.newOutputStream(filePath, openOptions)),
                    StandardCharsets.UTF_8),
                config.getBufferSize());
      } else {
        // Plain text output
        writer =
            new BufferedWriter(
                new OutputStreamWriter(
                    Files.newOutputStream(filePath, openOptions), StandardCharsets.UTF_8),
                config.getBufferSize());
      }

      isOpen = true;
      log.info(
          "Opened file destination: {} (format: {}, compress: {}, append: {}, batchSize: {})",
          filePath,
          serializer.getFormatName(),
          config.isCompress(),
          config.isAppend(),
          config.getBatchSize());

    } catch (IOException e) {
      throw new DestinationException("Failed to open file: " + config.getFilePath(), e);
    }
  }

  @Override
  public void write(Map<String, Object> record) {
    if (!isOpen) {
      throw new DestinationException("File destination not open. Call open() first.");
    }

    try {
      // Write CSV header on first record (only for CSV format)
      if (!headerWritten && "csv".equals(serializer.getFormatName())) {
        String header =
            ((com.datagenerator.formats.csv.CsvSerializer) serializer).serializeHeader(record);
        if (!header.isEmpty()) {
          writer.write(header);
          writer.write('\n');
          log.debug("Wrote CSV header: {}", header);
        }
        headerWritten = true;
      }

      // Serialize record and add to batch
      String line = serializer.serialize(record);
      batchBuffer.add(line);

      // Flush batch when full
      if (batchBuffer.size() >= config.getBatchSize()) {
        flushBatch();
      }

    } catch (IOException e) {
      throw new DestinationException("Failed to write record to file", e);
    }
  }

  /**
   * Flush accumulated batch of records to disk. Called automatically when batch is full, or
   * manually via flush() or close().
   */
  private void flushBatch() throws IOException {
    if (batchBuffer.isEmpty()) {
      return;
    }

    // Build batch string (reuse StringBuilder to reduce allocations)
    batchBuilder.setLength(0); // Clear previous content
    for (String line : batchBuffer) {
      batchBuilder.append(line).append('\n');
    }

    // Write entire batch in one call
    writer.write(batchBuilder.toString());

    // Clear batch for next accumulation
    batchBuffer.clear();

    log.debug("Flushed batch of {} records", batchBuffer.size());
  }

  @Override
  public void flush() {
    if (!isOpen) {
      log.warn("Cannot flush: file destination not open");
      return;
    }

    try {
      // Flush any remaining batch records first
      flushBatch();
      // Then flush the underlying writer
      writer.flush();
      log.debug("Flushed file destination: {}", config.getFilePath());
    } catch (IOException e) {
      throw new DestinationException("Failed to flush file", e);
    }
  }

  @Override
  public void close() {
    if (!isOpen) {
      log.debug("File destination already closed: {}", config.getFilePath());
      return;
    }

    try {
      flush();
      writer.close();
      isOpen = false;
      log.info("Closed file destination: {}", config.getFilePath());
    } catch (IOException e) {
      throw new DestinationException("Failed to close file", e);
    }
  }

  @Override
  public String getDestinationType() {
    return "file";
  }
}
