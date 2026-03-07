dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))
    
    // Kafka (compileOnly - users provide at runtime)
    compileOnly(libs.kafka.clients)
    testImplementation(libs.kafka.clients)
    
    // Database
    implementation(libs.hikaricp)
    compileOnly(libs.postgresql)
    compileOnly(libs.mysql.connector.j)
}
