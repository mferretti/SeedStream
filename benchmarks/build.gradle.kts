plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    // Project dependencies
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    implementation(project(":destinations"))
    
    // Fake data generation (for Datafaker benchmarks)
    implementation("net.datafaker:datafaker:2.5.4")
    
    // Kafka (for destination benchmarks)
    implementation("org.apache.kafka:kafka-clients:3.7.0")
    
    // Jackson (for serialization benchmarks)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    
    // OpenCSV (for CSV serialization benchmarks)
    implementation("com.opencsv:opencsv:5.9")
    
    // Test dependencies for memory profiling tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
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
