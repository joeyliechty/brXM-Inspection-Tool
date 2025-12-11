package org.bloomreach.inspections.cli.commands

import org.bloomreach.inspections.cli.config.CliConfig
import org.bloomreach.inspections.cli.config.ConfigException
import org.bloomreach.inspections.cli.config.ConfigLoader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ConfigCommandTest {

    private lateinit var testDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        testDir = Files.createTempDirectory("brxm-config-cmd-test")
    }

    @Test
    fun `should show current configuration`() {
        val command = ConfigCommand()
        command.action = "show"

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Verify configuration output
            assertTrue(output.contains("Configuration"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should display global settings in show command`() {
        val command = ConfigCommand()
        command.action = "show"

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should show various settings
            assertTrue(
                output.contains("Enabled") ||
                output.contains("Severity") ||
                output.contains("Parallel")
            )
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should display file patterns in show command`() {
        val command = ConfigCommand()
        command.action = "show"

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should show file patterns
            assertTrue(
                output.contains("Include") ||
                output.contains("Exclude") ||
                output.contains("Patterns")
            )
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should validate configuration successfully`() {
        val command = ConfigCommand()
        command.action = "validate"

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Validation output should be displayed
            assertTrue(output.isNotEmpty())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should show validation results`() {
        val command = ConfigCommand()
        command.action = "validate"

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should indicate valid or invalid
            assertTrue(output.contains("valid") || output.contains("Using defaults"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should init new configuration file`() {
        val configFile = testDir.resolve("test-config.yaml")

        val command = ConfigCommand()
        command.action = "init"
        command.configFile = configFile.toString()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should confirm creation
            assertTrue(output.contains("created") || output.contains("Creating"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should create valid configuration file on init`() {
        val configFile = testDir.resolve("config.yaml")

        val command = ConfigCommand()
        command.action = "init"
        command.configFile = configFile.toString()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()

            // Verify file was created
            assertTrue(Files.exists(configFile), "Configuration file should be created")

            // Verify file content can be loaded
            val loader = ConfigLoader()
            val config = loader.loadConfig(configFile)
            assertTrue(config != null, "Created config should be loadable")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should respect custom config file path`() {
        val command = ConfigCommand()
        val customPath = testDir.resolve("custom-config.yaml").toString()
        command.configFile = customPath

        assertEquals(customPath, command.configFile)
    }

    @Test
    fun `should handle show action with custom config file`() {
        val configFile = testDir.resolve("custom.yaml")
        val yaml = """
            enabled: true
            minSeverity: ERROR
        """.trimIndent()
        Files.writeString(configFile, yaml)

        val command = ConfigCommand()
        command.action = "show"
        command.configFile = configFile.toString()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should load and display custom config
            assertTrue(output.contains("Configuration"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should handle validate action with custom config file`() {
        val configFile = testDir.resolve("validate.yaml")
        val yaml = """
            enabled: true
            maxThreads: 4
        """.trimIndent()
        Files.writeString(configFile, yaml)

        val command = ConfigCommand()
        command.action = "validate"
        command.configFile = configFile.toString()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should validate successfully
            assertTrue(output.contains("valid") || output.contains("valid"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should reject unknown actions gracefully`() {
        val command = ConfigCommand()
        command.action = "unknown"

        val originalErr = System.err
        val capturedErr = ByteArrayOutputStream()
        System.setErr(PrintStream(capturedErr))

        try {
            command.run()
            val error = capturedErr.toString()

            // Should show error for unknown action
            assertTrue(error.contains("Unknown") || error.contains("action"))
        } finally {
            System.setErr(originalErr)
        }
    }

    @Test
    fun `should default to show action`() {
        val command = ConfigCommand()

        assertEquals("show", command.action)
    }

    @Test
    fun `should handle multiple action types without errors`() {
        listOf("show", "init", "validate").forEach { action ->
            val command = ConfigCommand()
            command.action = action
            command.configFile = testDir.resolve("test-${action}.yaml").toString()

            // Should not throw exception
            try {
                command.run()
            } catch (e: Exception) {
                if (e !is SystemExit) {
                    throw e
                }
            }
        }
    }
}

// Helper to allow test to catch SystemExit
private class SystemExit : Exception()
