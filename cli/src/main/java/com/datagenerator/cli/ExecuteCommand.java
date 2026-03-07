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

package com.datagenerator.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.datagenerator.core.engine.GenerationEngine;
import com.datagenerator.core.seed.SeedConfig;
import com.datagenerator.core.seed.SeedResolver;
import com.datagenerator.core.structure.StructureLoader;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.file.FileDestination;
import com.datagenerator.destinations.file.FileDestinationConfig;
import com.datagenerator.destinations.kafka.KafkaDestination;
import com.datagenerator.destinations.kafka.KafkaDestinationConfig;
import com.datagenerator.formats.FormatSerializer;
import com.datagenerator.formats.csv.CsvSerializer;
import com.datagenerator.formats.json.JsonSerializer;
import com.datagenerator.formats.protobuf.ProtobufSerializer;
import com.datagenerator.generators.DataGenerator;
import com.datagenerator.generators.DataGeneratorFactory;
import com.datagenerator.generators.GeneratorContext;
import com.datagenerator.schema.model.DataStructure;
import com.datagenerator.schema.model.JobConfig;
import com.datagenerator.schema.parser.DataStructureParser;
import com.datagenerator.schema.parser.JobConfigParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Execute command for running data generation jobs.
 *
 * <p>This command is the primary entry point for generating test data. It reads a job configuration
 * file, loads the data structure definition, and generates the specified number of records using
 * configured generators and destinations.
 *
 * <p><b>Basic Usage:</b>
 *
 * <pre>
 * # Generate 1000 records to file with default settings
 * datagenerator execute --job config/jobs/file_address.yaml --count 1000
 *
 * # Generate with specific format and seed for reproducibility
 * datagenerator execute --job config/jobs/file_user.yaml --format csv --seed 12345
 *
 * # Stream to Kafka with debug logging and custom thread count
 * datagenerator execute --job config/jobs/kafka_events.yaml --count 50000 --threads 8 --debug
 * </pre>
 *
 * <p><b>Common Scenarios:</b>
 *
 * <pre>
 * # Development: Small dataset with debug output
 * datagenerator execute --job my_job.yaml --count 10 --debug
 *
 * # Testing: Reproducible dataset
 * datagenerator execute --job my_job.yaml --count 1000 --seed 42
 *
 * # Production: High-volume generation
 * datagenerator execute --job my_job.yaml --count 1000000 --threads 16
 * </pre>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>Default thread count matches CPU cores for optimal performance
 *   <li>Batch sizes configured in job config affect throughput
 *   <li>File compression trades CPU for disk space
 *   <li>Kafka async mode significantly faster than sync
 * </ul>
 *
 * <p><b>Error Scenarios:</b>
 *
 * <ul>
 *   <li><b>Job file not found:</b> Check path relative to CLI working directory
 *   <li><b>Structure file not found:</b> Verify structures_path in job config
 *   <li><b>Kafka connection failed:</b> Check bootstrap server and credentials
 *   <li><b>Seed resolution failed:</b> Verify seed file/env var exists
 * </ul>
 *
 * <p><b>Thread Safety:</b> This command is thread-safe. Each execution creates isolated generator
 * instances and destinations.
 *
 * @since 1.0
 */
@Slf4j
@Command(
    name = "execute",
    description = "Execute a data generation job",
    mixinStandardHelpOptions = true)
public class ExecuteCommand implements Callable<Integer> {

  /**
   * Path to the job configuration YAML file.
   *
   * <p>The job config defines:
   *
   * <ul>
   *   <li>Data structure to generate (source field)
   *   <li>Destination type (file, kafka, database)
   *   <li>Seed configuration for reproducibility
   *   <li>Destination-specific settings
   * </ul>
   *
   * <p><b>Example:</b> {@code config/jobs/file_user.yaml}
   *
   * <p>Paths are resolved relative to the CLI working directory. Use {@code ../} for relative paths
   * when running from Gradle (working directory is cli/).
   *
   * @see com.datagenerator.schema.model.JobConfig
   */
  @Option(
      names = {"-j", "--job"},
      description = "Path to job configuration YAML file",
      required = true)
  private Path jobFile;

  /**
   * Output format for serialized records.
   *
   * <p>Supported formats:
   *
   * <ul>
   *   <li><b>json</b> - Newline-delimited JSON (default)
   *   <li><b>csv</b> - Comma-separated values with header row
   *   <li><b>protobuf</b> - Protocol Buffers binary format (base64-encoded)
   * </ul>
   *
   * <p><b>Format Selection:</b> JSON preserves nested structures and arrays. CSV flattens nested
   * objects and represents arrays as pipe-delimited strings. Protobuf provides compact binary
   * encoding (typically 50-70% smaller than JSON).
   *
   * <p>Default: {@code json}
   *
   * @see com.datagenerator.formats.json.JsonSerializer
   * @see com.datagenerator.formats.csv.CsvSerializer
   * @see com.datagenerator.formats.protobuf.ProtobufSerializer
   */
  @Option(
      names = {"-f", "--format"},
      description = "Output format: json, csv, protobuf (default: json)",
      defaultValue = "json")
  private String format;

  /**
   * Number of records to generate.
   *
   * <p>Controls the total number of records produced by the generation engine. Records are
   * generated across multiple worker threads for performance.
   *
   * <p><b>Performance Impact:</b> Generation time scales linearly with count. Typical throughput:
   * 50,000-200,000 records/second depending on structure complexity and destination.
   *
   * <p>Default: {@code 100}
   *
   * @see GenerationEngine
   */
  @Option(
      names = {"-c", "--count"},
      description = "Number of records to generate (default: 100)",
      defaultValue = "100")
  private int count;

  /**
   * Seed value for deterministic random generation.
   *
   * <p>When provided, overrides the seed configuration in the job config. Useful for:
   *
   * <ul>
   *   <li>Testing: Generate identical datasets across runs
   *   <li>Debugging: Reproduce specific data patterns
   *   <li>Partitioning: Generate different subsets with different seeds
   * </ul>
   *
   * <p><b>Example:</b> {@code --seed 12345} produces the same data every time.
   *
   * <p>If not provided, uses seed from job config (embedded, file, env, or remote).
   *
   * @see com.datagenerator.core.seed.SeedResolver
   * @see com.datagenerator.core.random.RandomProvider
   */
  @Option(
      names = {"-s", "--seed"},
      description = "Seed value (overrides job config)")
  private Long seedOverride;

  /**
   * Enable verbose output with DEBUG level logging.
   *
   * <p>Shows:
   *
   * <ul>
   *   <li>Configuration details (seed resolution, structure loading)
   *   <li>Major operations (generator creation, destination setup)
   *   <li>Performance metrics (records/second, elapsed time)
   * </ul>
   *
   * <p>Useful for understanding what the generator is doing without overwhelming detail.
   *
   * @see #debug
   */
  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  private boolean verbose;

  /**
   * Enable debug output with detailed TRACE level logging.
   *
   * <p>Shows all verbose information plus:
   *
   * <ul>
   *   <li>Individual record generation details (sampled)
   *   <li>Field-level generation for nested structures
   *   <li>Kafka record send confirmations
   *   <li>Structure loading and validation details
   *   <li>Type parsing and generator initialization
   * </ul>
   *
   * <p>TRACE logs are sampled to prevent overwhelming output. Use {@code --trace-sample} to control
   * sampling rate (default: 10%).
   *
   * <p><b>Performance Impact:</b> Minimal on generation speed, but log I/O may slow overall
   * execution depending on sample rate.
   *
   * @see #verbose
   * @see #traceSample
   */
  @Option(
      names = {"-d", "--debug"},
      description = "Enable debug output with TRACE logging (sampled)")
  private boolean debug;

  /**
   * Trace log sampling rate (percentage).
   *
   * <p>Controls what percentage of TRACE log statements are executed when {@code --debug} is
   * enabled. This prevents overwhelming log output when generating large datasets.
   *
   * <p><b>Sample Rates:</b>
   *
   * <ul>
   *   <li><b>100</b> - Log everything (useful for small datasets or detailed debugging)
   *   <li><b>10</b> - Log ~10% of TRACE statements (default, good balance)
   *   <li><b>1</b> - Log ~1% (useful for million-record generation)
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * # Debug 100 records with full TRACE output
   * --debug --trace-sample 100
   *
   * # Debug 100K records with 10% sampling (default)
   * --debug
   *
   * # Debug 1M records with 1% sampling
   * --debug --trace-sample 1
   * </pre>
   *
   * <p>This option only has effect when {@code --debug} is enabled.
   *
   * @see #debug
   */
  @Option(
      names = {"--trace-sample"},
      description = "Trace sampling rate percentage (1-100, default: 10)",
      defaultValue = "10")
  private int traceSample;

  /**
   * Number of worker threads for parallel generation.
   *
   * <p>Controls the level of parallelism for record generation. Each worker thread:
   *
   * <ul>
   *   <li>Has its own Random instance (seeded deterministically)
   *   <li>Generates a subset of the total record count
   *   <li>Uses thread-local GeneratorContext for Datafaker instances
   * </ul>
   *
   * <p><b>Performance Tuning:</b>
   *
   * <ul>
   *   <li>Default: Number of CPU cores (optimal for most workloads)
   *   <li>CPU-bound: Use CPU core count
   *   <li>I/O-bound (Kafka, DB): Can increase beyond core count
   *   <li>Too many threads: Overhead from context switching
   * </ul>
   *
   * <p>Default: {@code Runtime.getRuntime().availableProcessors()}
   *
   * @see GenerationEngine
   */
  @Option(
      names = {"-t", "--threads"},
      description = "Number of worker threads (default: # of CPU cores)")
  private Integer threads;

  /**
   * Executes the data generation job.
   *
   * <p>This method orchestrates the entire generation process:
   *
   * <ol>
   *   <li>Configure logging level based on --verbose/--debug flags
   *   <li>Parse job configuration from YAML file
   *   <li>Resolve seed (from override, config, or default)
   *   <li>Load data structure definition
   *   <li>Create format serializer (JSON/CSV)
   *   <li>Create and open destination adapter (File/Kafka/Database)
   *   <li>Build generator factory with structure registry
   *   <li>Create multi-threaded generation engine
   *   <li>Generate records in parallel
   *   <li>Flush and close destination
   * </ol>
   *
   * <p><b>Thread Safety:</b> Each invocation creates isolated instances. Safe to call multiple
   * times in sequence or from different threads.
   *
   * <p><b>Error Handling:</b>
   *
   * <ul>
   *   <li>{@link com.datagenerator.schema.exception.SchemaParseException} - Invalid YAML syntax or
   *       missing files
   *   <li>{@link com.datagenerator.core.exception.SeedResolutionException} - Seed file/env var not
   *       found
   *   <li>{@link IllegalArgumentException} - Unsupported format or destination type
   *   <li>{@link com.datagenerator.destinations.DestinationException} - Connection or write
   *       failures
   * </ul>
   *
   * @return 0 on success, non-zero on error (Picocli standard)
   * @throws Exception if generation fails (logged before throwing)
   */
  @Override
  public Integer call() throws Exception {
    // Configure logging level based on flags
    configureLoggingLevel();

    log.info("Starting data generation job");
    log.info("Job file: {}", jobFile);
    log.info("Format: {}, Count: {}, Seed override: {}", format, count, seedOverride);
    log.debug("Verbose: {}, Debug: {}", verbose, debug);

    // 1. Parse job configuration
    JobConfigParser jobParser = new JobConfigParser();
    JobConfig jobConfig = jobParser.parse(jobFile);
    log.info("Loaded job config: source={}, type={}", jobConfig.getSource(), jobConfig.getType());

    // 2. Resolve seed
    long seed = resolveSeed(jobConfig);
    log.info("Using seed: {}", seed);

    // 3. Load data structure
    Path structuresPath;
    if (jobConfig.getStructuresPath() != null) {
      structuresPath = Paths.get(jobConfig.getStructuresPath());
    } else {
      // Default to config/structures relative to job file location
      Path jobDir = jobFile.getParent();
      if (jobDir != null && jobDir.endsWith("jobs")) {
        structuresPath = jobDir.getParent().resolve("structures");
      } else {
        structuresPath = Paths.get("config/structures");
      }
    }

    DataStructureParser structureParser = new DataStructureParser();
    Path structureFile = structuresPath.resolve(jobConfig.getSource());
    DataStructure dataStructure = structureParser.parse(structureFile);
    log.info(
        "Loaded data structure: {} with {} fields",
        dataStructure.getName(),
        dataStructure.getData().size());

    // 4. Create format serializer
    FormatSerializer serializer = createSerializer(format);
    log.info("Created serializer: {}", serializer.getFormatName());

    // 5. Create destination adapter
    DestinationAdapter destination = createDestination(jobConfig, serializer);
    log.info("Created destination: {}", destination.getDestinationType());

    // 6. Set up generation context
    StructureRegistry registry = createStructureRegistry(structuresPath);
    DataGeneratorFactory factory = new DataGeneratorFactory(registry, structuresPath);

    // 7. Generate and write records using GenerationEngine
    destination.open();

    ObjectType objectType = new ObjectType(dataStructure.getName());
    DataGenerator generator = factory.create(objectType);

    // Determine number of worker threads
    int workerThreads = threads != null ? threads : Runtime.getRuntime().availableProcessors();

    // Create generation engine with context-aware record generator
    String geolocation = dataStructure.getGeolocation();
    GenerationEngine engine =
        GenerationEngine.builder()
            .recordGenerator(
                (random) -> {
                  // Each worker thread needs its own GeneratorContext
                  try (var ctx = GeneratorContext.enter(factory, geolocation)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record =
                        (Map<String, Object>) generator.generate(random, objectType);
                    return record;
                  }
                })
            .recordWriter(destination::write)
            .masterSeed(seed)
            .workerThreads(workerThreads)
            .build();

    log.info("Generating {} records...", count);
    long startTime = System.currentTimeMillis();

    // Generate records
    engine.generate(count);

    destination.flush();

    long elapsed = System.currentTimeMillis() - startTime;
    double recordsPerSec = count / (elapsed / 1000.0);

    log.info("Generation complete!");
    log.info("Total records: {}", count);
    log.info("Time elapsed: {} ms ({} records/sec)", elapsed, String.format("%.2f", recordsPerSec));

    destination.close();

    return 0; // Success
  }

  /**
   * Resolves the seed value for deterministic random generation.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Command-line --seed override (if provided)
   *   <li>Job config seed (embedded/file/env/remote)
   *   <li>Default value: 0 (with warning)
   * </ol>
   *
   * <p>If seed resolution from config fails (e.g., file not found, env var missing), falls back to
   * default value 0 and logs an error.
   *
   * @param jobConfig the job configuration containing seed config
   * @return resolved seed value (never null)
   */
  private long resolveSeed(JobConfig jobConfig) {
    if (seedOverride != null) {
      log.info("Using seed override from command line: {}", seedOverride);
      return seedOverride;
    }

    SeedConfig seedConfig = jobConfig.getSeed();
    if (seedConfig == null) {
      log.warn("No seed configuration found, using default: 0");
      return 0L;
    }

    try {
      SeedResolver resolver = new SeedResolver();
      long resolvedSeed = resolver.resolve(seedConfig);
      log.debug("Resolved seed from config: {}", resolvedSeed);
      return resolvedSeed;
    } catch (Exception e) {
      log.error("Failed to resolve seed, using default: 0", e);
      return 0L;
    }
  }

  /**
   * Creates a format serializer based on the specified format string.
   *
   * @param format format name ("json" or "csv", case-insensitive)
   * @return serializer instance for the specified format
   * @throws IllegalArgumentException if format is unsupported
   */
  private FormatSerializer createSerializer(String format) {
    return switch (format.toLowerCase()) {
      case "json" -> new JsonSerializer();
      case "csv" -> new CsvSerializer();
      case "protobuf" -> new ProtobufSerializer();
      default -> throw new IllegalArgumentException("Unsupported format: " + format);
    };
  }

  private DestinationAdapter createDestination(JobConfig jobConfig, FormatSerializer serializer) {
    String type = jobConfig.getType();
    JsonNode conf = jobConfig.getConf();

    return switch (type.toLowerCase()) {
      case "file" -> createFileDestination(conf, serializer);
      case "kafka" -> createKafkaDestination(conf, serializer);
      case "database" ->
          throw new UnsupportedOperationException("Database destination not yet implemented");
      default -> throw new IllegalArgumentException("Unsupported destination type: " + type);
    };
  }

  private FileDestination createFileDestination(JsonNode conf, FormatSerializer serializer) {
    String pathStr = conf.get("path").asText();
    boolean compress = conf.has("compress") && conf.get("compress").asBoolean();
    boolean append = conf.has("append") && conf.get("append").asBoolean();

    FileDestinationConfig config =
        FileDestinationConfig.builder()
            .filePath(Paths.get(pathStr + "." + serializer.getFormatName())) // Add extension
            .compress(compress)
            .append(append)
            .build();

    return new FileDestination(config, serializer);
  }

  private KafkaDestination createKafkaDestination(JsonNode conf, FormatSerializer serializer) {
    String bootstrap = conf.get("bootstrap").asText();
    String topic = conf.get("topic").asText();

    KafkaDestinationConfig.KafkaDestinationConfigBuilder configBuilder =
        KafkaDestinationConfig.builder().bootstrap(bootstrap).topic(topic);

    // Optional configuration
    if (conf.has("sync")) {
      configBuilder.sync(conf.get("sync").asBoolean());
    }
    if (conf.has("batch_size")) {
      configBuilder.batchSize(conf.get("batch_size").asInt());
    }
    if (conf.has("linger_ms")) {
      configBuilder.lingerMs(conf.get("linger_ms").asInt());
    }
    if (conf.has("compression")) {
      configBuilder.compression(conf.get("compression").asText());
    }
    if (conf.has("acks")) {
      configBuilder.acks(conf.get("acks").asText());
    }

    // SASL/SSL configuration
    if (conf.has("security_protocol")) {
      configBuilder.securityProtocol(conf.get("security_protocol").asText());
    }
    if (conf.has("sasl_mechanism")) {
      configBuilder.saslMechanism(conf.get("sasl_mechanism").asText());
    }
    if (conf.has("sasl_jaas_config")) {
      configBuilder.saslJaasConfig(conf.get("sasl_jaas_config").asText());
    }
    if (conf.has("ssl_truststore_location")) {
      configBuilder.sslTruststoreLocation(conf.get("ssl_truststore_location").asText());
    }
    if (conf.has("ssl_truststore_password")) {
      configBuilder.sslTruststorePassword(conf.get("ssl_truststore_password").asText());
    }
    if (conf.has("ssl_keystore_location")) {
      configBuilder.sslKeystoreLocation(conf.get("ssl_keystore_location").asText());
    }
    if (conf.has("ssl_keystore_password")) {
      configBuilder.sslKeystorePassword(conf.get("ssl_keystore_password").asText());
    }

    return new KafkaDestination(configBuilder.build(), serializer);
  }

  private StructureRegistry createStructureRegistry(Path structuresPath) {
    StructureLoader loader =
        (structureName, basePath, registry) -> {
          try {
            DataStructureParser parser = new DataStructureParser();
            Path structureFile = basePath.resolve(structureName + ".yaml");
            DataStructure structure = parser.parse(structureFile);

            // Convert Map<String, FieldDefinition> to Map<String, DataType>
            com.datagenerator.core.type.TypeParser typeParser =
                new com.datagenerator.core.type.TypeParser();
            return structure.getData().entrySet().stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> typeParser.parse(entry.getValue().getDatatype())));
          } catch (Exception e) {
            throw new RuntimeException("Failed to load structure: " + structureName, e);
          }
        };

    return new StructureRegistry(loader);
  }

  /**
   * Configure logging level based on CLI flags.
   *
   * <p>Logging levels:
   *
   * <ul>
   *   <li>Default: INFO (errors, warnings, progress)
   *   <li>Verbose: DEBUG (configuration, major operations)
   *   <li>Debug: TRACE (all operations including sampled record-level details)
   * </ul>
   *
   * <p>When debug mode is enabled, also sets the trace sample rate system property for use by
   * {@link com.datagenerator.core.util.LogUtils}.
   */
  private void configureLoggingLevel() {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Logger appLogger = (Logger) LoggerFactory.getLogger("com.datagenerator");

    if (debug) {
      // Enable TRACE logging
      rootLogger.setLevel(Level.TRACE);
      appLogger.setLevel(Level.TRACE);

      // Set trace sample rate for LogUtils
      // Validate range (1-100)
      int validatedSampleRate = Math.max(1, Math.min(100, traceSample));
      System.setProperty("com.datagenerator.traceSampleRate", String.valueOf(validatedSampleRate));

      log.debug(
          "Debug mode enabled - log level set to TRACE (sample rate: {}%)", validatedSampleRate);
    } else if (verbose) {
      rootLogger.setLevel(Level.DEBUG);
      appLogger.setLevel(Level.DEBUG);
      log.debug("Verbose mode enabled - log level set to DEBUG");
    } else {
      rootLogger.setLevel(Level.INFO);
      appLogger.setLevel(Level.INFO);
    }
  }
}
