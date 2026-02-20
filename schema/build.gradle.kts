dependencies {
    implementation(project(":core"))
    
    // YAML parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    
    // Configuration validation
    implementation("org.hibernate.validator:hibernate-validator:9.1.0.Final")
    implementation("org.glassfish:jakarta.el:4.0.2") // Required for Hibernate Validator
}
