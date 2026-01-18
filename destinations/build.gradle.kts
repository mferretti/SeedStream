dependencies {
    implementation(project(":core"))
    implementation(project(":formats"))
    
    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.6.1")
    
    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    compileOnly("org.postgresql:postgresql:42.7.1")
    compileOnly("com.mysql:mysql-connector-j:8.2.0")
}
