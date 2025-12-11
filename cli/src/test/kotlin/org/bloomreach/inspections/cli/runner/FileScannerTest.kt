package org.bloomreach.inspections.cli.runner

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.Severity
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileScannerTest {

    private lateinit var scanner: FileScanner
    private lateinit var testDir: java.nio.file.Path

    @BeforeEach
    fun setUp() {
        testDir = Files.createTempDirectory("brxm-scanner-test")

        val config = InspectionConfig(
            enabled = true,
            minSeverity = Severity.INFO,
            includePaths = listOf("**/*.java", "**/*.xml"),
            excludePaths = listOf("**/target/**", "**/build/**")
        )

        scanner = FileScanner(config)
    }

    @Test
    fun `should scan directory and find matching files`() {
        // Create test directory structure
        val srcDir = testDir.resolve("src/main/java")
        Files.createDirectories(srcDir)
        Files.createFile(srcDir.resolve("Test.java"))
        Files.createFile(srcDir.resolve("Example.java"))

        val xmlDir = testDir.resolve("src/main/resources")
        Files.createDirectories(xmlDir)
        Files.createFile(xmlDir.resolve("config.xml"))

        val results = scanner.scan(testDir)

        assertEquals(3, results.size)
        assertTrue(results.any { it.name == "Test.java" })
        assertTrue(results.any { it.name == "Example.java" })
        assertTrue(results.any { it.name == "config.xml" })
    }

    @Test
    fun `should apply glob patterns to file matching`() {
        val srcDir = testDir.resolve("src/main/java")
        Files.createDirectories(srcDir)
        Files.createFile(srcDir.resolve("Test.java"))
        Files.createFile(srcDir.resolve("readme.txt"))

        val results = scanner.scan(testDir)

        // Java files should be included, txt files excluded per config patterns
        assertTrue(results.any { it.name == "Test.java" })
        assertTrue(results.none { it.name == "readme.txt" })
    }

    @Test
    fun `should handle nested directories`() {
        val deepDir = testDir.resolve("src/main/java/org/example/app")
        Files.createDirectories(deepDir)
        Files.createFile(deepDir.resolve("Main.java"))

        val results = scanner.scan(testDir)

        assertEquals(1, results.size)
        assertEquals("Main.java", results.first().name)
    }

    @Test
    fun `should return empty list when no files match`() {
        val config = InspectionConfig(
            enabled = true,
            minSeverity = Severity.INFO,
            includePaths = listOf("**/*.scala"),
            excludePaths = emptyList()
        )
        val scalaScanner = FileScanner(config)

        val javaDir = testDir.resolve("src/main/java")
        Files.createDirectories(javaDir)
        Files.createFile(javaDir.resolve("Test.java"))

        val results = scalaScanner.scan(testDir)

        assertEquals(0, results.size)
    }

    @Test
    fun `should ignore files that don't match include patterns`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.createFile(srcDir.resolve("test.txt"))
        Files.createFile(srcDir.resolve("readme.md"))
        Files.createFile(srcDir.resolve("code.java"))

        val results = scanner.scan(testDir)

        assertEquals(1, results.size)
        assertEquals("code.java", results.first().name)
    }

    @Test
    fun `should handle files with no extension`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.createFile(srcDir.resolve("Dockerfile"))

        val results = scanner.scan(testDir)

        assertEquals(0, results.size)
    }

    @Test
    fun `should handle multiple glob patterns`() {
        val config = InspectionConfig(
            enabled = true,
            minSeverity = Severity.INFO,
            includePaths = listOf("**/*.java", "**/*.xml", "**/*.yaml"),
            excludePaths = emptyList()
        )
        val multiScanner = FileScanner(config)

        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.createFile(srcDir.resolve("Code.java"))
        Files.createFile(srcDir.resolve("config.xml"))
        Files.createFile(srcDir.resolve("app.yaml"))
        Files.createFile(srcDir.resolve("readme.txt"))

        val results = multiScanner.scan(testDir)

        assertEquals(3, results.size)
    }

    @Test
    fun `should correctly implement PathVirtualFile interface`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("Test.java")
        val content = "public class Test {}"
        Files.writeString(testFile, content)

        val vf = PathVirtualFile(testFile)

        assertEquals("Test.java", vf.name)
        assertEquals("java", vf.extension)
        assertTrue(vf.exists())
        assertEquals(testFile, vf.path)
        assertEquals(content.length.toLong(), vf.size())
        assertEquals(content, vf.readText())
    }

    @Test
    fun `should handle file modification time`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("Test.java")
        Files.writeString(testFile, "class Test {}")

        val vf = PathVirtualFile(testFile)

        assertTrue(vf.lastModified() > 0)
    }

    @Test
    fun `should have correct equals and hashCode for PathVirtualFile`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val file1 = srcDir.resolve("Test.java")
        Files.writeString(file1, "")

        val vf1 = PathVirtualFile(file1)
        val vf2 = PathVirtualFile(file1)
        val vf3 = PathVirtualFile(srcDir.resolve("Other.java"))

        assertEquals(vf1, vf2)
        assertEquals(vf1.hashCode(), vf2.hashCode())
        assertTrue(vf1 != vf3)
    }
}
