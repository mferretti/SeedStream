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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Execute command for running data generation jobs.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * datagenerator execute --job config/jobs/file_address.yaml --count 1000 --format json
 * </pre>
 */
@Slf4j
@Command(
    name = "execute",
    description = "Execute a data generation job",
    mixinStandardHelpOptions = true)
public class ExecuteCommand implements Callable<Integer> {

  @Option(
      names = {"-j", "--job"},
      description = "Path to job configuration YAML file",
      required = true)
  private Path jobFile;

  @Option(
      names = {"-f", "--format"},
      description = "Output format: json, csv (default: json)",
      defaultValue = "json")
  private String format;

  @Option(
      names = {"-c", "--count"},
      description = "Number of records to generate (default: 100)",
      defaultValue = "100")
  private int count;

  @Option(
      names = {"-s", "--seed"},
      description = "Seed value (overrides job config)")
  private Long seedOverride;

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  private boolean verbose;

  @Option(
      names = {"-t", "--threads"},
      description = "Number of worker threads (default: # of CPU cores)")
  private Integer threads;

  @Override
  public Integer call() throws Exception {
    log.info("Starting data generation job");
    log.info("Job file: {}", jobFile);
    log.info("Format: {}, Count: {}, Seed override: {}", format, count, seedOverride);

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

  private FormatSerializer createSerializer(String format) {
    return switch (format.toLowerCase()) {
      case "json" -> new JsonSerializer();
      case "csv" -> new CsvSerializer();
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
}
