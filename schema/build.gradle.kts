dependencies {
    implementation(project(":core"))

    // YAML parsing
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.databind)

    // Configuration validation
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.el) // Required for Hibernate Validator

    // AWS Secrets Manager resolver
    // sync client uses apache-client; netty only serves the async client
    implementation(libs.aws.secretsmanager) { exclude(group = "software.amazon.awssdk", module = "netty-nio-client") }

    // Azure Key Vault resolver
    implementation(libs.azure.keyvault.secrets) { exclude(group = "com.azure", module = "azure-core-http-netty") }
    implementation(libs.azure.identity) { exclude(group = "com.azure", module = "azure-core-http-netty") }
    // JDK HttpClient instead of netty
    implementation(libs.azure.core.http.jdk.httpclient)

    // Google Cloud Secret Manager resolver
    implementation(libs.gcp.secretmanager)

    // Integration test containers
    testImplementation(libs.testcontainers.vault)
    testImplementation(libs.testcontainers.localstack)

    // Logback test appender for asserting on WARN log output
    testImplementation(libs.logback.classic)
}
