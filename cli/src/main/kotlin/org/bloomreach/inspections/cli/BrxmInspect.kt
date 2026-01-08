package org.bloomreach.inspections.cli

import org.bloomreach.inspections.cli.commands.*
import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

/**
 * Main CLI entry point for Bloomreach CMS Inspections Tool.
 *
 * Provides commands for:
 * - Analyzing projects
 * - Listing available inspections
 * - Managing configuration
 * - Generating reports
 */
@Command(
    name = "brxm-inspect",
    description = ["Bloomreach CMS Static Analysis Tool"],
    mixinStandardHelpOptions = true,
    subcommands = [
        AnalyzeCommand::class,
        ListInspectionsCommand::class,
        ConfigCommand::class
    ]
)
class BrxmInspect : Runnable {
    override fun run() {
        // When no command is specified, show help
        CommandLine(this).usage(System.out)
    }
}

/**
 * Main function - entry point for the CLI
 */
fun main(args: Array<String>) {
    val cmd = CommandLine(BrxmInspect())
    cmd.commandSpec.version(getVersion())
    val exitCode = cmd.execute(*args)
    exitProcess(exitCode)
}

/**
 * Reads the version from the generated version.properties file.
 * The version is populated at build time from gradle.properties.
 */
fun getVersion(): String {
    return try {
        val resource = BrxmInspect::class.java.getResourceAsStream("/org/bloomreach/inspections/cli/version.properties")
        if (resource != null) {
            val props = java.util.Properties()
            props.load(resource)
            props.getProperty("version", "unknown")
        } else {
            "unknown"
        }
    } catch (e: Exception) {
        "unknown"
    }
}
