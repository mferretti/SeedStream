dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))
    
    // Kafka (compileOnly - users provide at runtime)
    compileOnly(libs.kafka.clients)
    testImplementation(libs.kafka.clients)
    
    // Database
    implementation(libs.hikaricp)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql)  // needed for integration tests against real PostgreSQL
}
