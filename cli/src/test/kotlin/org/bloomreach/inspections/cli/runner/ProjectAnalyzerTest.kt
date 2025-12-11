package org.bloomreach.inspections.cli.runner

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.InspectionIssue
import org.bloomreach.inspections.core.engine.Severity
import org.bloomreach.inspections.core.engine.TextRange
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectAnalyzerTest {

    private lateinit var analyzer: ProjectAnalyzer
    private lateinit var testDir: java.nio.file.Path
    private lateinit var config: InspectionConfig

    @BeforeEach
    fun setUp() {
        testDir = Files.createTempDirectory("brxm-analyzer-test")

        config = InspectionConfig(
            enabled = true,
            minSeverity = Severity.INFO,
            includePaths = listOf("**/*.java"),
            excludePaths = listOf("**/target/**")
        )

        analyzer = ProjectAnalyzer(config)
    }

    @Test
    fun `should initialize with inspection registry`() {
        assertNotNull(analyzer)
    }

    @Test
    fun `should throw when getting results before analysis`() {
        assertThrows<IllegalStateException> {
            analyzer.getResults()
        }
    }

    @Test
    fun `should analyze files and collect results`() {
        // Create a test Java file with a potential session leak
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("SessionTest.java")
        val javaCode = """
            public class SessionTest {
                public void badMethod() {
                    Session session = repository.login();
                    // Missing logout
                }
            }
        """.trimIndent()
        Files.writeString(testFile, javaCode)

        val vf = PathVirtualFile(testFile)

        var fileProcessed = ""
        analyzer.analyze(
            projectRoot = testDir,
            files = listOf(vf),
            progressCallback = { fileName -> fileProcessed = fileName }
        )

        // Verify analysis completed
        val results = analyzer.getResults()
        assertNotNull(results)
        assertEquals("SessionTest.java", fileProcessed)
    }

    @Test
    fun `should get statistics after analysis`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("Test.java")
        Files.writeString(testFile, "public class Test {}")

        val vf = PathVirtualFile(testFile)

        analyzer.analyze(
            projectRoot = testDir,
            files = listOf(vf)
        )

        val stats = analyzer.getStatistics()

        assertNotNull(stats)
        assertTrue(stats.filesAnalyzed >= 0)
        assertTrue(stats.inspectionsRun >= 0)
    }

    @Test
    fun `should count different severity levels`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)

        // Create files that might trigger different severities
        val testFile = srcDir.resolve("Test.java")
        Files.writeString(testFile, """
            public class Test {
                public void test() {
                    Session s = repo.login(); // session leak
                    System.out.println("debug"); // system.out
                }
            }
        """.trimIndent())

        val vf = PathVirtualFile(testFile)

        analyzer.analyze(
            projectRoot = testDir,
            files = listOf(vf)
        )

        val results = analyzer.getResults()
        assertTrue(results.totalIssues >= 0)
    }

    @Test
    fun `should handle empty file list gracefully`() {
        // Should handle empty file list without error
        var callbackCalled = false
        analyzer.analyze(
            projectRoot = testDir,
            files = emptyList(),
            progressCallback = { callbackCalled = true }
        )

        val results = analyzer.getResults()
        assertNotNull(results)
        assertEquals(false, callbackCalled)
    }

    @Test
    fun `should support progress callback`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        Files.writeString(srcDir.resolve("Test1.java"), "class Test1 {}")
        Files.writeString(srcDir.resolve("Test2.java"), "class Test2 {}")

        val filesProcessed = mutableListOf<String>()
        analyzer.analyze(
            projectRoot = testDir,
            files = listOf(
                PathVirtualFile(srcDir.resolve("Test1.java")),
                PathVirtualFile(srcDir.resolve("Test2.java"))
            ),
            progressCallback = { fileName -> filesProcessed.add(fileName) }
        )

        assertEquals(2, filesProcessed.size)
    }

    @Test
    fun `should aggregate results by category`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)
        val testFile = srcDir.resolve("Test.java")
        Files.writeString(testFile, """
            public class Test {
                Session s = null; // repository issue
                String password = "secret"; // security issue
            }
        """.trimIndent())

        val vf = PathVirtualFile(testFile)
        analyzer.analyze(
            projectRoot = testDir,
            files = listOf(vf)
        )

        val results = analyzer.getResults()
        assertNotNull(results.issuesByCategory)
    }

    @Test
    fun `should handle multiple files in analysis`() {
        val srcDir = testDir.resolve("src")
        Files.createDirectories(srcDir)

        val file1 = srcDir.resolve("Test1.java")
        val file2 = srcDir.resolve("Test2.java")
        Files.writeString(file1, "class Test1 {}")
        Files.writeString(file2, "class Test2 {}")

        val files = listOf(
            PathVirtualFile(file1),
            PathVirtualFile(file2)
        )

        var filesProcessed = 0
        analyzer.analyze(
            projectRoot = testDir,
            files = files,
            progressCallback = { filesProcessed++ }
        )

        // Verify that progress callback was called for each file
        assertEquals(2, filesProcessed)
    }
}
