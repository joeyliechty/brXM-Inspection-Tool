package org.bloomreach.inspections.core.inspections.security

import org.bloomreach.inspections.core.config.InspectionConfig
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.model.ProjectIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Paths

class XmlExternalEntityInspectionTest {

    private val inspection = XmlExternalEntityInspection()

    @Test
    fun `should detect DocumentBuilderFactory without XXE protection`() {
        val code = """
            package com.example;

            import javax.xml.parsers.DocumentBuilderFactory;

            public class VulnerableXmlParser {
                public void parseXml(String xml) throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    // Missing XXE protection
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertEquals(Severity.ERROR, issues[0].severity)
        assertTrue(issues[0].message.contains("DocumentBuilderFactory"))
    }

    @Test
    fun `should not flag DocumentBuilderFactory with XXE protection`() {
        val code = """
            package com.example;

            import javax.xml.parsers.DocumentBuilderFactory;

            public class SecureXmlParser {
                public void parseXml(String xml) throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect XMLInputFactory without XXE protection`() {
        val code = """
            package com.example;

            import javax.xml.stream.XMLInputFactory;

            public class VulnerableStreamParser {
                public void parse(String xml) throws Exception {
                    XMLInputFactory factory = XMLInputFactory.newInstance();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("XMLInputFactory"))
    }

    @Test
    fun `should not flag XMLInputFactory with ACCESS_EXTERNAL_DTD protection`() {
        val code = """
            package com.example;

            import javax.xml.stream.XMLInputFactory;
            import javax.xml.XMLConstants;

            public class SecureStreamParser {
                public void parse(String xml) throws Exception {
                    XMLInputFactory factory = XMLInputFactory.newInstance();
                    factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect SAXParserFactory without XXE protection`() {
        val code = """
            package com.example;

            import javax.xml.parsers.SAXParserFactory;

            public class VulnerableSaxParser {
                public void parse(String xml) throws Exception {
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(1, issues.size)
        assertTrue(issues[0].message.contains("SAXParserFactory"))
    }

    @Test
    fun `should not flag SAXParserFactory with FEATURE_SECURE_PROCESSING`() {
        val code = """
            package com.example;

            import javax.xml.parsers.SAXParserFactory;
            import javax.xml.XMLConstants;

            public class SecureSaxParser {
                public void parse(String xml) throws Exception {
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                    spf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(0, issues.size)
    }

    @Test
    fun `should detect multiple vulnerable parsers`() {
        val code = """
            package com.example;

            import javax.xml.parsers.*;

            public class MultipleVulnerable {
                public void test() throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    SAXParserFactory spf = SAXParserFactory.newInstance();
                }
            }
        """.trimIndent()

        val issues = runInspection(code)

        assertEquals(2, issues.size)
    }

    @Test
    fun `should not flag test files`() {
        val code = """
            package com.example;

            import javax.xml.parsers.DocumentBuilderFactory;

            public class XmlParserTest {
                public void testParsing() throws Exception {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                }
            }
        """.trimIndent()

        val file = createTestFile("XmlParserTest.java", code)
        val issues = runInspection(code, file)

        assertEquals(0, issues.size)
    }

    private fun runInspection(
        code: String,
        file: VirtualFile = createVirtualFile("Test.java", code)
    ): List<InspectionIssue> {
        val context = InspectionContext(
            projectRoot = Paths.get("/test"),
            file = file,
            fileContent = code,
            language = FileType.JAVA,
            config = InspectionConfig.default(),
            cache = InspectionCache(),
            projectIndex = ProjectIndex()
        )
        return inspection.inspect(context)
    }

    private fun createVirtualFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: java.nio.file.Path = Paths.get("/test/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }

    private fun createTestFile(name: String, content: String): VirtualFile {
        return object : VirtualFile {
            override val path: java.nio.file.Path = Paths.get("/test/src/test/$name")
            override val name: String = name
            override val extension: String = "java"
            override fun readText(): String = content
            override fun exists(): Boolean = true
            override fun size(): Long = content.length.toLong()
            override fun lastModified(): Long = System.currentTimeMillis()
        }
    }
}
