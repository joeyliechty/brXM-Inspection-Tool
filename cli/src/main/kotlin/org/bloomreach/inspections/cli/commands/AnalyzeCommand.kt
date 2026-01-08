package org.bloomreach.inspections.cli.commands

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.bloomreach.inspections.cli.config.ConfigLoader
import org.bloomreach.inspections.cli.runner.FileScanner
import org.bloomreach.inspections.cli.runner.ProjectAnalyzer
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.Severity
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

/**
 * Analyze command - runs inspections on a Bloomreach project.
 */
@Command(
    name = "analyze",
    description = ["Analyze a Bloomreach CMS project"]
)
class AnalyzeCommand : Runnable {

    @Parameters(
        index = "0",
        description = ["Project directory to analyze"]
    )
    lateinit var projectDir: String

    @Option(
        names = ["-c", "--config"],
        description = ["Configuration file (YAML)"]
    )
    var configFile: String? = null

    @Option(
        names = ["-o", "--output"],
        description = ["Output directory for reports (default: ./output)"]
    )
    var outputDir: String = "./output"

    @Option(
        names = ["-f", "--format"],
        description = ["Report formats: html, markdown, json, all (default: markdown)"],
        split = ","
    )
    var formats: Array<String> = arrayOf("markdown")

    @Option(
        names = ["-i", "--inspection"],
        description = ["Run specific inspections by ID (comma-separated)"],
        split = ","
    )
    var inspectionIds: Array<String>? = null

    @Option(
        names = ["-e", "--exclude"],
        description = ["Exclude file patterns (glob)"],
        split = ","
    )
    var excludePatterns: Array<String>? = null

    @Option(
        names = ["--severity"],
        description = ["Minimum severity: ERROR, WARNING, INFO, HINT (default: INFO)"]
    )
    var minSeverity: String = "INFO"

    @Option(
        names = ["-t", "--threads"],
        description = ["Number of parallel threads (default: CPU cores)"]
    )
    var threads: Int = Runtime.getRuntime().availableProcessors()

    @Option(
        names = ["-v", "--verbose"],
        description = ["Verbose output"]
    )
    var verbose: Boolean = false

    @Option(
        names = ["--no-cache"],
        description = ["Disable parse cache"]
    )
    var noCache: Boolean = false

    override fun run() {
        try {
            // Configure logging based on verbose flag
            if (verbose) {
                val loggerContext = org.slf4j.LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext
                val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
                rootLogger.level = ch.qos.logback.classic.Level.DEBUG
            }

            println("Bloomreach CMS Inspections - Analyzing project: $projectDir")
            println("=" .repeat(80))

            // Create configuration
            val config = createConfig()

            // Scan project for files
            val projectPath = Paths.get(projectDir)
            if (!projectPath.toFile().exists()) {
                System.err.println("Error: Project directory does not exist: $projectDir")
                return
            }

            val scanner = FileScanner(config)
            val files = scanner.scan(projectPath)

            println("Found ${files.size} files to analyze\n")

            if (files.isEmpty()) {
                println("No files found to analyze. Check your include/exclude patterns.")
                return
            }

            // Analyze project with progress bar
            val analyzer = ProjectAnalyzer(config)
            var currentFile = ""

            val progressBar = ProgressBarBuilder()
                .setTaskName("Analyzing")
                .setInitialMax(files.size.toLong())
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
                .setUpdateIntervalMillis(100)
                .build()

            val results = progressBar.use { pb ->
                val elapsed = measureTimeMillis {
                    analyzer.analyze(
                        projectRoot = projectPath,
                        files = files,
                        progressCallback = { fileName ->
                            currentFile = fileName
                            pb.step()
                            // Fixed-width filename display (50 chars) to prevent progress bar jumping
                            val maxWidth = 50
                            val display = if (fileName.length > maxWidth - 3) {
                                "..." + fileName.takeLast(maxWidth - 3)
                            } else {
                                fileName.padEnd(maxWidth)
                            }
                            pb.extraMessage = display
                        }
                    )
                }

                println("\nAnalysis complete in ${elapsed / 1000.0}s")
                analyzer.getResults()
            }

            // Print summary
            printSummary(results)

            // Generate reports
            if (results.totalIssues > 0) {
                generateReports(results)
            } else {
                println("\n✓ No issues found! Your project looks great.")
            }

        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
        }
    }

    private fun createConfig(): InspectionConfig {
        val configLoader = ConfigLoader()
        val cliConfig = configLoader.loadConfigFromString(configFile)

        // Start with file config or defaults
        val baseConfig = cliConfig ?: org.bloomreach.inspections.cli.config.CliConfig.default()
        val defaultConfig = org.bloomreach.inspections.core.config.InspectionConfig.default()

        // Command-line options override config file, which overrides defaults
        val severity = Severity.valueOf(minSeverity.uppercase())
        val excludePaths = excludePatterns?.toList()
            ?: baseConfig.excludePaths
            ?: defaultConfig.excludePaths
        val includePaths = baseConfig.includePaths
            ?: defaultConfig.includePaths

        return InspectionConfig(
            enabled = baseConfig.enabled ?: true,
            minSeverity = severity,
            parallel = baseConfig.parallel ?: (threads > 1),
            maxThreads = threads,
            cacheEnabled = baseConfig.cacheEnabled ?: !noCache,
            excludePaths = excludePaths,
            includePaths = includePaths
        )
    }

    private fun printSummary(results: org.bloomreach.inspections.core.engine.InspectionResults) {
        println("\nSummary:")
        println("  Total issues: ${results.totalIssues}")
        println("  Errors: ${results.errorCount} 🔴")
        println("  Warnings: ${results.warningCount} 🟡")
        println("  Info: ${results.infoCount} 🔵")
        println("  Hints: ${results.hintCount} 💡")

        if (results.issuesByCategory.isNotEmpty()) {
            println("\nBy Category:")
            results.issuesByCategory.forEach { (category, issues) ->
                println("  ${category.displayName}: ${issues.size}")
            }
        }
    }

    private fun generateReports(results: org.bloomreach.inspections.core.engine.InspectionResults) {
        println("\nGenerating reports in: $outputDir")

        try {
            val outputPath = Paths.get(outputDir)
            java.nio.file.Files.createDirectories(outputPath)

            // Extract project name for filename prefix
            val projectPath = Paths.get(projectDir).toAbsolutePath().normalize()
            val projectName = projectPath.fileName.toString()

            val generatedReports = mutableListOf<String>()

            // Determine which formats to generate
            val requestedFormats = if ("all" in formats) {
                arrayOf("html", "markdown", "json")
            } else {
                formats
            }

            // Generate reports based on requested formats
            for (format in requestedFormats) {
                when (format.lowercase()) {
                    "json" -> {
                        val generator = org.bloomreach.inspections.core.reports.JsonReportGenerator()
                        val reportPath = outputPath.resolve("${projectName}-inspection-report.json")
                        generator.generate(results, reportPath)
                        generatedReports.add("  ✓ JSON report: ${reportPath.toAbsolutePath()}")
                    }
                    "markdown", "md" -> {
                        val generator = org.bloomreach.inspections.core.reports.MarkdownReportGenerator()
                        val reportPath = outputPath.resolve("${projectName}-inspection-report.md")
                        generator.generate(results, reportPath)
                        generatedReports.add("  ✓ Markdown report: ${reportPath.toAbsolutePath()}")
                    }
                    "html" -> {
                        val generator = org.bloomreach.inspections.core.reports.HtmlReportGenerator()
                        val reportPath = outputPath.resolve("${projectName}-inspection-report.html")
                        generator.generate(results, reportPath)
                        generatedReports.add("  ✓ HTML report: ${reportPath.toAbsolutePath()}")
                    }
                    else -> {
                        println("  ⚠ Unknown format: $format")
                    }
                }
            }

            // Print generated reports
            generatedReports.forEach { println(it) }

        } catch (e: Exception) {
            System.err.println("Error generating reports: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
        }
    }
}
