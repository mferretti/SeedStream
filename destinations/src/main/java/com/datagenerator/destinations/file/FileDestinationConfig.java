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
   * Buffer size for writes (bytes). Higher values = better performance, more memory. Default: 8KB
   */
  @Builder.Default int bufferSize = 8192;
}
