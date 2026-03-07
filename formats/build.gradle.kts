dependencies {
    implementation(project(":core"))
    
    // JSON
    implementation(libs.bundles.jackson)
    
    // CSV
    implementation(libs.opencsv)
    
    // Protobuf
    implementation(libs.protobuf.java)
}
