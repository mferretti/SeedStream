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

import com.datagenerator.destinations.AbstractDestination;
import com.datagenerator.destinations.DestinationException;
import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.FormatSerializer.StreamWriter;
import com.datagenerator.formats.avro.AvroSerializer;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;

/**
 * Writes generated records to files using Java NIO for high performance.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Java NIO for fast I/O
 *   <li>Buffered writes (configurable buffer size via {@link
 *       FileDestinationConfig#getBufferSize()})
 *   <li>Optional gzip compression (serial "stream" mode or parallel "per_chunk" mode)
 *   <li>Append mode support
 *   <li>CSV header row (for CSV format)
 *   <li>Automatic parent directory creation
 *   <li>Zero-copy JSON streaming via {@link FormatSerializer#createStreamWriter} — no intermediate
 *       String allocation per record
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
public class FileDestination extends AbstractDestination {
  private static final String MODE_STREAM = "stream";
  private static final String MODE_PER_CHUNK = "per_chunk";

  private final FileDestinationConfig config;
  private final FormatSerializer serializer;

  private boolean headerWritten = false;
  private boolean perChunkGzip = false;
  private boolean anyChunkWritten = false;

  // Text-format state
  private OutputStream outputStream;
  private StreamWriter streamWriter;

  // Avro container-format state (used only when serializer is AvroSerializer)
  private final boolean isAvro;
  private OutputStream avroRawOut;
  private DataFileWriter<GenericRecord> avroFileWriter;

  /**
   * Create file destination with configuration and serializer.
   *
   * @param config file output configuration
   * @param serializer format serializer (JSON, CSV, etc.)
   */
  public FileDestination(FileDestinationConfig cfg, FormatSerializer ser) {
    this.config = cfg;
    this.serializer = ser;
    this.isAvro = serializer instanceof AvroSerializer;
  }

  @Override
  public void open() {
    if (isOpen) {
      log.warn("File destination already open: {}", config.getFilePath());
      return;
    }

    validateCompressMode();

    try {
      Path filePath = config.getFilePath();

      // Create parent directories if they don't exist
      Path parentDir = filePath.getParent();
      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
        log.debug("Created parent directories: {}", parentDir);
      }

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

      if (isAvro) {
        // Avro Object Container Format — DataFileWriter handles its own buffering and compression.
        // The DataFileWriter is initialized lazily on first write when the schema is known.
        avroRawOut = Files.newOutputStream(filePath, openOptions);
      } else {
        if (config.isCompress() && !filePath.toString().endsWith(".gz")) {
          filePath = Path.of(filePath.toString() + ".gz");
        }
        OutputStream base = Files.newOutputStream(filePath, openOptions);
        // When perChunkGzip is true, do NOT wrap GZIPOutputStream here; each chunk is gzipped
        // on the worker thread and the writer concatenates the members.
        if (config.isCompress() && !perChunkGzip) {
          base = new GZIPOutputStream(base);
        }
        outputStream = new BufferedOutputStream(base, config.getBufferSize());
        streamWriter = serializer.createStreamWriter(outputStream);
      }

      isOpen = true;
      log.info(
          "Opened file destination: {} (format: {}, compress: {}, append: {})",
          filePath,
          serializer.getFormatName(),
          config.isCompress(),
          config.isAppend());

    } catch (IOException e) {
      throw new DestinationException("Failed to open file: " + config.getFilePath(), e);
    }
  }

  /** Validate {@code compress_mode} and resolve the {@link #perChunkGzip} flag (fail fast). */
  private void validateCompressMode() {
    String compressMode = config.getCompressMode();
    if (!MODE_STREAM.equals(compressMode) && !MODE_PER_CHUNK.equals(compressMode)) {
      throw new DestinationException(
          "Unknown compress_mode '" + compressMode + "'. Valid values: stream, per_chunk");
    }
    boolean perChunk = MODE_PER_CHUNK.equals(compressMode);
    if (perChunk && !config.isCompress()) {
      throw new DestinationException("compress_mode: per_chunk requires compress: true");
    }
    if (perChunk && !supportsWriteCoalescing()) {
      throw new DestinationException(
          "compress_mode: per_chunk requires an NDJSON-style format; csv/avro serialize on the writer thread");
    }
    perChunkGzip = config.isCompress() && perChunk;
  }

  @Override
  public void write(Map<String, Object> data) {
    requireOpen("File");

    if (perChunkGzip) {
      throw new DestinationException("per_chunk compression requires the serialized write path");
    }

    if (isAvro) {
      writeAvro(data);
      return;
    }

    try {
      if (!headerWritten && serializer instanceof com.datagenerator.formats.csv.CsvSerializer csv) {
        String header = csv.serializeHeader(data);
        if (!header.isEmpty()) {
          outputStream.write(header.getBytes(StandardCharsets.UTF_8));
          outputStream.write('\n');
          log.debug("Wrote CSV header: {}", header);
        }
        headerWritten = true;
      }

      streamWriter.writeRecord(data);

    } catch (IOException e) {
      throw new DestinationException("Failed to write record to file", e);
    }
  }

  @Override
  public boolean supportsSerializedWrite() {
    // Avro OCF must be serialized on the writer thread (ordered container). CSV needs the record's
    // keys to emit its header row, which the raw-bytes path does not carry. Everything else (JSON
    // NDJSON, etc.) appends an independently-encoded record + newline.
    return !isAvro && !(serializer instanceof com.datagenerator.formats.csv.CsvSerializer);
  }

  @Override
  public void writeSerialized(byte[] payload) {
    requireOpen("File");
    try {
      if (perChunkGzip) {
        // Defensive: engine never calls this when coalescing is wired, but keep the contract valid.
        byte[] framed = new byte[payload.length + 1];
        System.arraycopy(payload, 0, framed, 0, payload.length);
        framed[payload.length] = '\n';
        outputStream.write(gzipMember(framed));
      } else {
        outputStream.write(payload);
        outputStream.write('\n');
      }
      anyChunkWritten = true;
    } catch (IOException e) {
      throw new DestinationException("Failed to write serialized record to file", e);
    }
  }

  @Override
  public boolean supportsWriteCoalescing() {
    // Same eligibility as supportsSerializedWrite(): plain NDJSON-style formats, not Avro OCF or
    // CSV (header handling needs the record's keys, which raw bytes don't carry).
    return supportsSerializedWrite();
  }

  @Override
  public byte[] coalesce(List<byte[]> payloads) {
    int size = 0;
    for (byte[] payload : payloads) {
      size += payload.length + 1; // +1 for the trailing newline this method adds per record
    }
    byte[] combined = new byte[size];
    int pos = 0;
    for (byte[] payload : payloads) {
      System.arraycopy(payload, 0, combined, pos, payload.length);
      pos += payload.length;
      combined[pos++] = '\n';
    }
    // If per_chunk gzip mode, wrap the combined payload as an independent gzip member.
    if (perChunkGzip) {
      return gzipMember(combined);
    }
    return combined;
  }

  @Override
  public void writeSerializedChunk(byte[] coalescedPayload) {
    requireOpen("File");
    try {
      // When perChunkGzip is true, coalesce() has already gzipped the payload as an independent
      // member; write as-is. Otherwise, coalesce() returns plain concatenated bytes.
      outputStream.write(coalescedPayload);
      anyChunkWritten = true;
    } catch (IOException e) {
      throw new DestinationException("Failed to write coalesced records to file", e);
    }
  }

  /**
   * Gzip-compress a raw byte array as an independent gzip member.
   *
   * <p>Runs on worker threads during parallel generation. Thread-safe and stateless. Returns a
   * complete gzip member that can be written to the output stream; multiple members are
   * concatenated to form a valid multi-member .gz file (RFC 1952).
   *
   * @param raw raw bytes to compress
   * @return gzipped bytes (complete gzip member)
   * @throws DestinationException if compression fails
   */
  private byte[] gzipMember(byte[] raw) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
    try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
      gz.write(raw);
    } catch (IOException e) {
      throw new DestinationException("Failed to gzip chunk", e);
    }
    return baos.toByteArray();
  }

  private void writeAvro(Map<String, Object> data) {
    try {
      AvroSerializer avroSer = (AvroSerializer) serializer;
      if (avroFileWriter == null) {
        avroSer.ensureInitialized(data);
        GenericDatumWriter<GenericRecord> dw = new GenericDatumWriter<>(avroSer.getSchema());
        avroFileWriter = new DataFileWriter<>(dw);
        if (config.isCompress()) {
          avroFileWriter.setCodec(CodecFactory.deflateCodec(6));
        }
        avroFileWriter.create(avroSer.getSchema(), avroRawOut);
      }
      avroFileWriter.append(avroSer.buildGenericRecord(data));
    } catch (IOException e) {
      throw new DestinationException("Failed to write Avro record", e);
    }
  }

  @Override
  public void flush() {
    if (!isOpen) {
      log.warn("Cannot flush: file destination not open");
      return;
    }

    try {
      if (isAvro) {
        if (avroFileWriter != null) {
          avroFileWriter.flush();
        }
      } else {
        outputStream.flush();
      }
      log.debug("Flushed file destination: {}", config.getFilePath());
    } catch (IOException e) {
      throw new DestinationException("Failed to flush file", e);
    }
  }

  @Override
  public void close() {
    if (!isOpen) {
      log.debug("File destination already closed: {}", config.getFilePath());
      // open() may have allocated a stream before failing prior to setting isOpen — release any
      // such partially-opened resources.
      closeQuietly();
      return;
    }

    try {
      if (isAvro) {
        if (avroFileWriter != null) {
          avroFileWriter.close(); // also closes avroRawOut
        } else if (avroRawOut != null) {
          avroRawOut.close();
        }
      } else {
        // If per_chunk gzip and no chunks were written, emit an empty member so the .gz is valid.
        if (perChunkGzip && !anyChunkWritten) {
          outputStream.write(gzipMember(new byte[0]));
        }
        flush();
        streamWriter.close();
        outputStream.close();
      }
      isOpen = false;
      log.info("Closed file destination: {}", config.getFilePath());
    } catch (IOException e) {
      throw new DestinationException("Failed to close file", e);
    }
  }

  /**
   * Best-effort, null-safe cleanup of any resources {@link #open()} may have allocated before
   * failing partway through (i.e. before {@code isOpen} was set to {@code true}). Never throws.
   */
  private void closeQuietly() {
    try {
      if (streamWriter != null) {
        streamWriter.close();
      }
    } catch (IOException e) {
      log.warn("Failed to close StreamWriter during cleanup", e);
    }
    try {
      if (outputStream != null) {
        outputStream.close();
      }
    } catch (IOException e) {
      log.warn("Failed to close OutputStream during cleanup", e);
    }
    try {
      if (avroFileWriter != null) {
        avroFileWriter.close(); // also closes avroRawOut
      } else if (avroRawOut != null) {
        avroRawOut.close();
      }
    } catch (IOException e) {
      log.warn("Failed to close Avro writer during cleanup", e);
    }
  }

  @Override
  public String getDestinationType() {
    return "file";
  }
}
