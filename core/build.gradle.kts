dependencies {
    // Configuration
    implementation(libs.bundles.jackson)

    // Validation
    implementation(libs.jakarta.validation.api)

    // Fake data generation
    implementation(libs.datafaker)

    // Regex string generator for the `regex:` --faker-types (used directly, not via Datafaker's
    // shaded copy). See DatafakerRegistry.registerRegex.
    implementation(libs.rgxgen)

    // Logback test appender for asserting on WARN log output
    testImplementation(libs.logback.classic)
}
