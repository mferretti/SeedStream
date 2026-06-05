dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))

    // Kafka (compileOnly - users provide at runtime)
    compileOnly(libs.kafka.clients)
    testImplementation(libs.kafka.clients)

    // Avro (for DataFileWriter in FileDestination — formats uses implementation scope)
    implementation(libs.avro)

    // Database
    implementation(libs.hikaricp)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql) // needed for integration tests against real PostgreSQL

    // Full pipeline IT tests (ref[] e2e)
    testImplementation(project(":schema"))
    testImplementation(project(":generators"))
}
