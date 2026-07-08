dependencies {
    // Configuration
    implementation(libs.bundles.jackson)

    // Validation
    implementation(libs.jakarta.validation.api)

    // Fake data generation
    implementation(libs.datafaker)

    // Logback test appender for asserting on WARN log output
    testImplementation(libs.logback.classic)
}
