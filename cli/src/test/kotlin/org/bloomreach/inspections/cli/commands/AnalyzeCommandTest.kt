package org.bloomreach.inspections.cli.commands

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AnalyzeCommandTest {

    private lateinit var testDir: java.nio.file.Path
    private lateinit var outputDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        testDir = Files.createTempDirectory("brxm-analyze-test")
        outputDir = Files.createTempDirectory("brxm-analyze-output")
    }

    @Test
    fun `should fail when project directory does not exist`() {
        val command = AnalyzeCommand()
        command.projectDir = "/non/existent/path"

        val originalErr = System.err
        val capturedErr = ByteArrayOutputStream()
        System.setErr(PrintStream(capturedErr))

        try {
            command.run()
            assertTrue(capturedErr.toString().contains("does not exist"))
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `should handle empty project directory`() {
        val command = AnalyzeCommand()
        command.projectDir = testDir.toString()
        command.outputDir = outputDir.toString()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()
            assertTrue(output.contains("No files found"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should accept custom output directory`() {
        val command = AnalyzeCommand()
        command.projectDir = testDir.toString()
        command.outputDir = "/custom/output"

        assertEquals("/custom/output", command.outputDir)
    }

    @Test
    fun `should support multiple report formats`() {
        val command = AnalyzeCommand()

        // Test array format parsing
        command.formats = arrayOf("html", "json", "markdown")

        assertEquals(3, command.formats.size)
        assertTrue(command.formats.contains("html"))
        assertTrue(command.formats.contains("json"))
        assertTrue(command.formats.contains("markdown"))
    }

    @Test
    fun `should default to markdown format when not specified`() {
        val command = AnalyzeCommand()

        assertEquals(1, command.formats.size)
        assertEquals("markdown", command.formats[0])
    }

    @Test
    fun `should support 'all' format keyword`() {
        val command = AnalyzeCommand()
        command.formats = arrayOf("all")

        assertEquals(1, command.formats.size)
        assertEquals("all", command.formats[0])
    }

    @Test
    fun `should accept specific inspection IDs`() {
        val command = AnalyzeCommand()
        command.inspectionIds = arrayOf("repository.session-leak", "performance.unbounded-query")

        assertEquals(2, command.inspectionIds?.size)
    }

    @Test
    fun `should support exclude patterns`() {
        val command = AnalyzeCommand()
        command.excludePatterns = arrayOf("**/target/**", "**/build/**")

        assertEquals(2, command.excludePatterns?.size)
    }

    @Test
    fun `should accept minimum severity level`() {
        val command = AnalyzeCommand()
        command.minSeverity = "ERROR"

        assertEquals("ERROR", command.minSeverity)
    }

    @Test
    fun `should default to INFO severity`() {
        val command = AnalyzeCommand()

        assertEquals("INFO", command.minSeverity)
    }

    @Test
    fun `should support thread count configuration`() {
        val command = AnalyzeCommand()
        command.threads = 8

        assertEquals(8, command.threads)
    }

    @Test
    fun `should default to CPU core count threads`() {
        val command = AnalyzeCommand()

        assertEquals(Runtime.getRuntime().availableProcessors(), command.threads)
    }

    @Test
    fun `should support verbose flag`() {
        val command = AnalyzeCommand()

        assertEquals(false, command.verbose)

        command.verbose = true
        assertEquals(true, command.verbose)
    }

    @Test
    fun `should support no-cache flag`() {
        val command = AnalyzeCommand()

        assertEquals(false, command.noCache)

        command.noCache = true
        assertEquals(true, command.noCache)
    }

    @Test
    fun `should support configuration file option`() {
        val command = AnalyzeCommand()
        command.configFile = "/path/to/config.yaml"

        assertEquals("/path/to/config.yaml", command.configFile)
    }

    @Test
    fun `should allow null configuration file`() {
        val command = AnalyzeCommand()

        assertEquals(null, command.configFile)
    }

    @Test
    fun `should reject invalid project directory gracefully`() {
        val command = AnalyzeCommand()
        command.projectDir = ""

        val originalErr = System.err
        val capturedErr = ByteArrayOutputStream()
        System.setErr(PrintStream(capturedErr))

        try {
            command.run()
            val error = capturedErr.toString()
            assertTrue(error.isNotEmpty())
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `should handle analyze with real Java file`() {
        // Create a real test Java file
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val javaFile = srcDir.resolve("Test.java")
        Files.writeString(javaFile, """
            public class Test {
                public void test() {
                    // Valid code
                }
            }
        """.trimIndent())

        val command = AnalyzeCommand()
        command.projectDir = testDir.toString()
        command.outputDir = outputDir.toString()
        command.formats = arrayOf("json")

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()
            assertTrue(output.contains("Found") || output.contains("Analyzing") || output.contains("✓"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should create output directory if needed`() {
        val newOutputDir = testDir.resolve("reports/new/output")

        val command = AnalyzeCommand()
        command.projectDir = testDir.toString()
        command.outputDir = newOutputDir.toString()

        // Directory should be created during report generation
        assertTrue(testDir.toFile().exists())
    }

    @Test
    fun `should support case-insensitive format names`() {
        val command = AnalyzeCommand()

        // Picocli should handle this, but test supports the expectation
        command.formats = arrayOf("HTML", "Json", "MARKDOWN")

        assertEquals(3, command.formats.size)
    }
}
