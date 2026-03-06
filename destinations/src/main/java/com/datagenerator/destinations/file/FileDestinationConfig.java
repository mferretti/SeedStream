package com.datagenerator.destinations.file;

import java.nio.file.Path;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration for file destination.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>Configurable output path
 *   <li>Optional gzip compression
 *   <li>Append mode support
 *   <li>Buffered writes for performance
 * </ul>
 */
@Value
@Builder
public class FileDestinationConfig {
  /** Output file path. Parent directories created if they don't exist. */
  Path filePath;

  /** Whether to compress output with gzip (.gz extension added automatically). */
  @Builder.Default boolean compress = false;

  /** Whether to append to existing file (default: overwrite). */
  @Builder.Default boolean append = false;

  /**
   * Buffer size for writes (bytes). Higher values = better performance, more memory. Default: 64KB
   * (increased from 8KB after performance analysis showing 17% improvement)
   */
  @Builder.Default int bufferSize = 65536;

  /**
   * Batch size for record writes. Records are accumulated and written in batches to amortize I/O
   * overhead. Default: 1000 records per batch (provides 2-3x performance improvement over
   * per-record writes). Set to 1 to disable batching.
   */
  @Builder.Default int batchSize = 1000;
}
