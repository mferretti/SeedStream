dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))
    
    // Kafka
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    
    // Database
    implementation("com.zaxxer:HikariCP:7.0.2")
    compileOnly("org.postgresql:postgresql:42.7.10")
    compileOnly("com.mysql:mysql-connector-j:8.2.0")
}
