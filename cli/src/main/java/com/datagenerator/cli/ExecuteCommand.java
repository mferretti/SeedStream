package com.datagenerator.cli;

import com.datagenerator.core.seed.RandomProvider;
import com.datagenerator.core.seed.SeedConfig;
import com.datagenerator.core.seed.SeedResolver;
import com.datagenerator.core.structure.StructureLoader;
import com.datagenerator.core.structure.StructureRegistry;
import com.datagenerator.core.type.ObjectType;
import com.datagenerator.destinations.DestinationAdapter;
import com.datagenerator.destinations.file.FileDestination;
import com.datagenerator.destinations.file.FileDestinationConfig;
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
import java.util.Random;
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

    // 7. Generate and write records
    try (var ctx = GeneratorContext.enter(factory)) {
      destination.open();

      RandomProvider randomProvider = new RandomProvider(seed);
      Random random = randomProvider.getRandom();

      ObjectType objectType = new ObjectType(dataStructure.getName());
      DataGenerator generator = factory.create(objectType);

      log.info("Generating {} records...", count);
      long startTime = System.currentTimeMillis();

      for (int i = 0; i < count; i++) {
        @SuppressWarnings("unchecked")
        Map<String, Object> record = (Map<String, Object>) generator.generate(random, objectType);
        destination.write(record);

        if (verbose && (i + 1) % 100 == 0) {
          log.info("Generated {} records", i + 1);
        }
      }

      destination.flush();

      long elapsed = System.currentTimeMillis() - startTime;
      double recordsPerSec = count / (elapsed / 1000.0);

      log.info("Generation complete!");
      log.info("Total records: {}", count);
      log.info(
          "Time elapsed: {} ms ({} records/sec)", elapsed, String.format("%.2f", recordsPerSec));

    } finally {
      destination.close();
    }

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
      case "kafka" ->
          throw new UnsupportedOperationException("Kafka destination not yet implemented");
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
