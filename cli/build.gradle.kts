plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

dependencies {
    implementation(project(":core"))

    // Picocli for CLI framework
    implementation("info.picocli:picocli:4.7.5")

    // YAML parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Progress bar
    implementation("me.tongfei:progressbar:0.10.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

application {
    mainClass.set("org.bloomreach.inspections.cli.BrxmInspectKt")
    applicationName = "brxm-inspect"
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "org.bloomreach.inspections.cli.BrxmInspectKt"
        }

        // Create fat JAR with all dependencies
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    test {
        useJUnitPlatform()
    }
}
