plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":schema"))
    implementation(project(":generators"))
    implementation(project(":formats"))
    implementation(project(":destinations"))
    
    // Jackson for JSON processing
    implementation(libs.jackson.databind)
    
    // CLI framework
    implementation(libs.picocli)
    annotationProcessor(libs.picocli.codegen)
    
    // Logback for programmatic log level control
    implementation(libs.logback.classic)
    
    // Runtime dependencies for destinations (users can override versions)
    runtimeOnly(libs.kafka.clients)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql.connector.j)
}

application {
    mainClass.set("com.datagenerator.cli.DataGeneratorCli")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.datagenerator.cli.DataGeneratorCli"
    }
}
