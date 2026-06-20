dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))
    // Avro (for DataFileWriter in FileDestination — formats uses implementation scope)
    implementation(libs.avro)
    // Database
    implementation(libs.hikaricp)

    // Kafka (compileOnly - users provide at runtime)
    compileOnly(libs.kafka.clients)

    testImplementation(libs.kafka.clients)
    testImplementation(libs.h2)
    testImplementation(libs.postgresql) // needed for integration tests against real PostgreSQL
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.mysql.connector)
    // Oracle + SQL Server IT drivers/containers (tests tagged @slow — run via slowTest)
    testImplementation(libs.testcontainers.oracle.free)
    testImplementation(libs.ojdbc11)
    testImplementation(libs.testcontainers.mssqlserver)
    testImplementation(libs.mssql.jdbc)
    // Full pipeline IT tests (ref[] e2e)
    testImplementation(project(":schema"))
    testImplementation(project(":generators"))
}
