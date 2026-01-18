dependencies {
    implementation(project(":core"))
    
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    
    // CSV
    implementation("com.opencsv:opencsv:5.12.0")
    
    // Protobuf (optional, add when implementing)
    // implementation("com.google.protobuf:protobuf-java:3.25.1")
}
