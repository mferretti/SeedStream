dependencies {
    implementation(project(":core"))
    
    // JSON
    implementation(libs.bundles.jackson)
    
    // CSV
    implementation(libs.opencsv)
    
    // Protobuf (optional, add when implementing)
    // implementation(libs.protobuf.java)
}
