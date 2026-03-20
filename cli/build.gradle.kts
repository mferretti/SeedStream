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
    
    // Runtime dependencies for destinations
    runtimeOnly(libs.kafka.clients)
    // JDBC drivers are NOT bundled — users drop them into extras/ at runtime
}

application {
    mainClass.set("com.datagenerator.cli.DataGeneratorCli")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.datagenerator.cli.DataGeneratorCli"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("seedstream")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.datagenerator.cli.DataGeneratorCli"
        attributes["Implementation-Version"] = project.version
    }
    // Merge all runtime dependency JARs into one, excluding signature files that would
    // invalidate the merged JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
}

tasks.startScripts {
    doLast {
        // Unix: prepend extras/* to CLASSPATH so JARs dropped there are picked up at startup
        unixScript.writeText(
            unixScript.readText().replace(
                "CLASSPATH=\$APP_HOME/lib/",
                "CLASSPATH=\$APP_HOME/extras/*:\$APP_HOME/lib/"
            )
        )
        // Windows: same for the batch script
        windowsScript.writeText(
            windowsScript.readText().replace(
                "set CLASSPATH=%APP_HOME%\\lib\\",
                "set CLASSPATH=%APP_HOME%\\extras\\*;%APP_HOME%\\lib\\"
            )
        )
    }
}
