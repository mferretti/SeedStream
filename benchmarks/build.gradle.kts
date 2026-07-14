plugins {
    id("java")
    alias(libs.plugins.jmh)
}

dependencies {
    // Project dependencies
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    implementation(project(":destinations"))
    // Fake data generation (for Datafaker benchmarks)
    implementation(libs.datafaker)
    // Kafka (for destination benchmarks)
    implementation(libs.kafka.clients)
    // Jackson (for serialization benchmarks)
    implementation(libs.bundles.jackson)
    // OpenCSV (for CSV serialization benchmarks)
    implementation(libs.opencsv)

    // PostgreSQL driver — compileOnly in :destinations, so must be explicit here for JMH runtime
    runtimeOnly(libs.postgresql)

    // Test dependencies for memory profiling tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

// Enable test task for memory profiling tests
tasks.named<Test>("test") {
    useJUnitPlatform {
        // Exclude profiling tests by default (they are long-running)
        excludeTags("profiling")
    }
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

jmh {
    jmhVersion.set("1.37")

    // Benchmark configuration.
    // These defaults are load-bearing: every number published in docs/BENCHMARK-RESULTS.md was
    // measured with them, so changing them silently invalidates comparison against past runs.
    // For a higher-confidence run, use -PjmhFidelity=high (below) rather than editing these.
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    threads.set(1)

    // High-fidelity mode: ./gradlew :benchmarks:jmh -PjmhFidelity=high
    // More warmup and a second fork, for families whose default-config error bars are too wide to
    // publish (Datafaker generators in particular). Results are NOT comparable with default-config
    // runs — label them as such.
    if (project.findProperty("jmhFidelity") == "high") {
        warmupIterations.set(5)
        iterations.set(10)
        fork.set(2)
    }

    // Output format
    resultFormat.set("JSON")
    resultsFile.set(project.file("${project.layout.buildDirectory.get()}/reports/jmh/results.json"))

    // Suite filter: ./gradlew :benchmarks:jmh -PjmhSuite=database|kafka|generators|regex
    // Without -PjmhSuite, all benchmarks run (default JMH behaviour — match everything).
    if (project.hasProperty("jmhSuite")) {
        val suite = project.property("jmhSuite") as String
        val pattern = when (suite) {
            "database"   -> listOf(".*DatabaseBenchmark.*")
            "kafka"      -> listOf(".*KafkaBenchmark.*")
            "generators" -> listOf(".*(PrimitiveGenerators|DatafakerGenerators|CompositeGenerators|Serializer|Destination)Benchmark.*")
            "regex"      -> listOf(".*(DatafakerRegexType|DatafakerRegexCompile|DatafakerRegexp)Benchmark.*")
            else         -> throw GradleException("Unknown jmhSuite '$suite'. Valid values: database, kafka, generators, regex")
        }
        includes.set(pattern)
    }
    // Do NOT set includes unconditionally here — me.champeau.jmh 0.7.x: an explicit
    // includes.set(...) overrides -Pjmh.includes from the command line.
}
