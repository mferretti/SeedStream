plugins {
    id("java")
    id("me.champeau.jmh") version "0.7.2"
}

// Disable regular test task - benchmarks should only run via :benchmarks:jmh
tasks.named("test") {
    enabled = false
}

dependencies {
    // Project dependencies
    implementation(project(":core"))
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
