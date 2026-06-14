dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))

    // Schema parsing: OpenAPI specs are JSON/YAML, read with Jackson.
    // Parser libs stay isolated in this module — they never leak into the
    // runtime generation chain (cli -> destinations -> ... -> core).
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    // SQL DDL parsing (CREATE TABLE -> columns, types, foreign keys)
    implementation(libs.jsqlparser)

    // Protobuf FileDescriptorSet ingestion
    implementation(libs.protobuf.java)
}
