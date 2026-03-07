dependencies {
    implementation(project(":core"))
    
    // YAML parsing
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.databind)
    
    // Configuration validation
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.el) // Required for Hibernate Validator
}
