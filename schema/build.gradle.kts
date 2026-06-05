dependencies {
    implementation(project(":core"))

    // YAML parsing
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.databind)

    // Configuration validation
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.el) // Required for Hibernate Validator

    // AWS Secrets Manager resolver
    implementation(libs.aws.secretsmanager)

    // Azure Key Vault resolver
    implementation(libs.azure.keyvault.secrets)
    implementation(libs.azure.identity)
}
