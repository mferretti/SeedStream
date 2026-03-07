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
    
    // Test dependencies for memory profiling tests
    testImplementation(libs.junit.jupiter.benchmarks)
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
    
    // Include all benchmarks
    includes.set(listOf(".*"))
}
