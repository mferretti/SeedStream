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
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
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
    
    // Benchmark configuration
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
    threads.set(1)
    
    // Output format
    resultFormat.set("JSON")
    resultsFile.set(project.file("${project.layout.buildDirectory.get()}/reports/jmh/results.json"))

    // Suite filter: ./gradlew :benchmarks:jmh -PjmhSuite=database|kafka|generators
    // Without -PjmhSuite, all benchmarks run (default JMH behaviour — match everything).
    if (project.hasProperty("jmhSuite")) {
        val suite = project.property("jmhSuite") as String
        val pattern = when (suite) {
            "database"   -> listOf(".*DatabaseBenchmark.*")
            "kafka"      -> listOf(".*KafkaBenchmark.*")
            "generators" -> listOf(".*(PrimitiveGenerators|DatafakerGenerators|CompositeGenerators|Serializer|Destination)Benchmark.*")
            else         -> throw GradleException("Unknown jmhSuite '$suite'. Valid values: database, kafka, generators")
        }
        includes.set(pattern)
    }
    // Do NOT set includes unconditionally here — me.champeau.jmh 0.7.x: an explicit
    // includes.set(...) overrides -Pjmh.includes from the command line.
}
