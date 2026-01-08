package org.bloomreach.inspections.core.inspections.deployment

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectVersionInspectionTest {

    private val inspection = ProjectVersionInspection()

    @Test
    fun `should detect default version 0-1-0-SNAPSHOT`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.1.0-SNAPSHOT</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should detect default version")
        assertTrue(issues[0].message.contains("still at default"))
        assertEquals(Severity.HINT, issues[0].severity)
    }

    @Test
    fun `should not report issues for updated version`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>1.2.3</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should not report issues for updated versions")
    }

    @Test
    fun `should not report issues for version 0-0-1-SNAPSHOT`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.0.1-SNAPSHOT</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        // SNAPSHOT versions at 0.x.x are fine - they're development versions
        assertEquals(0, issues.size, "Should not report issues for development SNAPSHOT versions")
    }

    @Test
    fun `should detect low production version 0-0-1`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.0.1</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should detect very low production version")
        assertTrue(issues[0].message.contains("very low"))
        assertEquals(Severity.HINT, issues[0].severity)
    }

    @Test
    fun `should detect low production version 0-1-5`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.1.5</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should detect low production version at 0.1.x")
        assertTrue(issues[0].message.contains("very low"))
    }

    @Test
    fun `should not report issues for version 0-2-0`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.2.0</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should not report for 0.2.0 (indicates versioning was done)")
    }

    @Test
    fun `should not report issues for version 1-0-0`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>1.0.0</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should not report for production-ready 1.0.0")
    }

    @Test
    fun `should not report issues for version 2-3-1-with-qualifiers`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>2.3.1-rc1</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should not report for reasonable version numbers")
    }

    @Test
    fun `should ignore non-pom-xml files`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <version>0.1.0-SNAPSHOT</version>
            </project>
        """.trimIndent()

        val file = createVirtualFile("other-config.xml", xml)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should ignore non-pom.xml files")
    }

    @Test
    fun `should handle malformed XML gracefully`() {
        val malformedPom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <version>0.1.0-SNAPSHOT
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", malformedPom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        // Should not crash
        assertTrue(true)
    }

    @Test
    fun `should handle pom without version element`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should handle missing version element gracefully")
    }

    @Test
    fun `should handle version with extra whitespace`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <version>
                0.1.0-SNAPSHOT
              </version>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should detect default version even with whitespace")
    }

    @Test
    fun `should issue contains metadata`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <version>0.1.0-SNAPSHOT</version>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size)
        assertTrue(issues[0].metadata.containsKey("version"))
        assertTrue(issues[0].metadata.containsKey("reason"))
        assertEquals("0.1.0-SNAPSHOT", issues[0].metadata["version"])
    }

    @Test
    fun `case insensitive filename check`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <version>0.1.0-SNAPSHOT</version>
            </project>
        """.trimIndent()

        val file = createVirtualFile("POM.XML", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should check POM.XML case-insensitively")
    }

    @Test
    fun `should detect project version, not parent version`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <parent>
                <groupId>com.example</groupId>
                <artifactId>parent-pom</artifactId>
                <version>16.6.0</version>
              </parent>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>0.1.0-SNAPSHOT</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(1, issues.size, "Should detect project version 0.1.0-SNAPSHOT, not parent version 16.6.0")
        assertTrue(issues[0].message.contains("0.1.0-SNAPSHOT"))
    }

    @Test
    fun `should correctly extract project version when parent is present`() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
              <parent>
                <version>14.7.5</version>
              </parent>
              <groupId>org.example</groupId>
              <artifactId>my-project</artifactId>
              <version>2.5.0</version>
              <name>My Project</name>
            </project>
        """.trimIndent()

        val file = createVirtualFile("pom.xml", pom)
        val context = createContext(file)

        val issues = inspection.inspect(context)

        assertEquals(0, issues.size, "Should correctly identify project version 2.5.0 (not parent 14.7.5)")
    }

    // Helper methods

    private fun createVirtualFile(name: String, content: String, uniqueName: String = name): VirtualFile {
        return object : VirtualFile {
            override val path: Path = Path.of("/test/$uniqueName")
            override val name: String = name
            override val extension: String = name.substringAfterLast('.', "")
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }

    private fun createContext(
        file: VirtualFile,
        projectIndex: ProjectIndex = ProjectIndex()
    ): InspectionContext {
        return InspectionContext(
            file = file,
            fileContent = file.readText(),
            language = FileType.XML,
            projectRoot = Path.of("/test"),
            projectIndex = projectIndex,
            config = InspectionConfig(),
            cache = InspectionCache()
        )
    }
}
