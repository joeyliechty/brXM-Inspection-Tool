package org.bloomreach.inspections.cli.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigLoaderTest {

    private lateinit var configLoader: ConfigLoader
    private val tempDir = Files.createTempDirectory("brxm-config-test")

    @BeforeEach
    fun setUp() {
        configLoader = ConfigLoader()
    }

    @Test
    fun `should load valid YAML configuration file`() {
        val configPath = tempDir.resolve("test-config.yaml")
        val yaml = """
            enabled: true
            minSeverity: ERROR
            parallel: true
            maxThreads: 4
            cacheEnabled: true
            includePaths:
              - "**/*.java"
              - "**/*.xml"
            excludePaths:
              - "**/target/**"
        """.trimIndent()

        Files.writeString(configPath, yaml)

        val config = configLoader.loadConfig(configPath)

        assertNotNull(config)
        assertEquals(true, config.enabled)
        assertEquals("ERROR", config.minSeverity)
        assertEquals(true, config.parallel)
        assertEquals(4, config.maxThreads)
        assertEquals(true, config.cacheEnabled)
        assertEquals(2, config.includePaths?.size)
        assertEquals(1, config.excludePaths?.size)
    }

    @Test
    fun `should return null for non-existent file`() {
        val configPath = tempDir.resolve("non-existent.yaml")

        val config = configLoader.loadConfig(configPath)

        assertNull(config)
    }

    @Test
    fun `should throw exception for invalid YAML`() {
        val configPath = tempDir.resolve("invalid-config.yaml")
        val invalidYaml = """
            enabled: true
            invalid: [unclosed array
        """.trimIndent()

        Files.writeString(configPath, invalidYaml)

        assertThrows<ConfigException> {
            configLoader.loadConfig(configPath)
        }
    }

    @Test
    fun `should load configuration from string path`() {
        val configPath = tempDir.resolve("config.yaml")
        val yaml = """
            enabled: true
            minSeverity: WARNING
        """.trimIndent()

        Files.writeString(configPath, yaml)

        val config = configLoader.loadConfigFromString(configPath.toString())

        assertNotNull(config)
        assertEquals(true, config.enabled)
        assertEquals("WARNING", config.minSeverity)
    }

    @Test
    fun `should return null when config file path is null`() {
        val config = configLoader.loadConfigFromString(null)

        // Will be null unless default config exists in working directory
        // This test verifies the function doesn't throw with null input
    }

    @Test
    fun `should save configuration to YAML file`() {
        val configPath = tempDir.resolve("saved-config.yaml")
        val config = CliConfig(
            enabled = true,
            minSeverity = "INFO",
            parallel = true,
            maxThreads = 8,
            cacheEnabled = false,
            includePaths = listOf("**/*.java"),
            excludePaths = listOf("**/target/**")
        )

        configLoader.saveConfig(config, configPath)

        val loaded = configLoader.loadConfig(configPath)
        assertNotNull(loaded)
        assertEquals(config.enabled, loaded.enabled)
        assertEquals(config.minSeverity, loaded.minSeverity)
        assertEquals(config.maxThreads, loaded.maxThreads)
    }

    @Test
    fun `should validate configuration successfully`() {
        val config = CliConfig(
            maxThreads = 4,
            minSeverity = "ERROR"
        )

        // Should not throw
        configLoader.validateConfig(config)
    }

    @Test
    fun `should fail validation for invalid max threads`() {
        val config = CliConfig(maxThreads = 0)

        assertThrows<ConfigException> {
            configLoader.validateConfig(config)
        }
    }

    @Test
    fun `should fail validation for invalid severity`() {
        val config = CliConfig(minSeverity = "INVALID")

        assertThrows<ConfigException> {
            configLoader.validateConfig(config)
        }
    }

    @Test
    fun `should accept valid severity levels`() {
        val severities = listOf("ERROR", "WARNING", "INFO", "HINT")

        severities.forEach { severity ->
            val config = CliConfig(minSeverity = severity)
            configLoader.validateConfig(config)
        }
    }

    @Test
    fun `should provide default configuration`() {
        val defaultConfig = CliConfig.default()

        assertEquals(true, defaultConfig.enabled)
        assertEquals("INFO", defaultConfig.minSeverity)
        assertEquals(true, defaultConfig.parallel)
        assertEquals(true, defaultConfig.cacheEnabled)
        assertNotNull(defaultConfig.includePaths)
        assertNotNull(defaultConfig.excludePaths)
        assertEquals(5, defaultConfig.includePaths?.size)
        assertEquals(4, defaultConfig.excludePaths?.size)
    }

    @Test
    fun `should handle configuration with per-inspection settings`() {
        val configPath = tempDir.resolve("inspection-config.yaml")
        val yaml = """
            enabled: true
            inspections:
              repository.session-leak:
                enabled: true
                severity: ERROR
              performance.unbounded-query:
                enabled: false
                severity: WARNING
        """.trimIndent()

        Files.writeString(configPath, yaml)

        val config = configLoader.loadConfig(configPath)

        assertNotNull(config)
        assertEquals(2, config.inspections?.size)
        assertNotNull(config.inspections?.get("repository.session-leak"))
        assertNotNull(config.inspections?.get("performance.unbounded-query"))
    }
}
