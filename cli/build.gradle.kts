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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    
    // CLI framework
    implementation("info.picocli:picocli:4.7.7")
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
}

application {
    mainClass.set("com.datagenerator.cli.DataGeneratorCli")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.datagenerator.cli.DataGeneratorCli"
    }
}
