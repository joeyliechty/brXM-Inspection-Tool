package org.bloomreach.inspections.core.parsers.xml

import org.junit.jupiter.api.Test
import org.bloomreach.inspections.core.parsers.ParseResult
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XmlDoctypeTest {

    @Test
    fun `should parse XML with DOCTYPE declaration`() {
        val xmlWithDoctype = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<root>
    <element>Test</element>
</root>"""

        val parser = XmlParser.instance
        val result = parser.parse(xmlWithDoctype)

        assertTrue(result is ParseResult.Success)
        val doc = (result as ParseResult.Success).ast
        assertNotNull(doc)
        assertNotNull(doc.documentElement)
    }

    @Test
    fun `should parse simple XML without DOCTYPE`() {
        val simpleXml = """<?xml version="1.0" encoding="UTF-8"?>
<root>
    <element>Test</element>
</root>"""

        val parser = XmlParser.instance
        val result = parser.parse(simpleXml)

        assertTrue(result is ParseResult.Success)
        val doc = (result as ParseResult.Success).ast
        assertNotNull(doc)
        assertNotNull(doc.documentElement)
    }

    @Test
    fun `should gracefully handle empty files`() {
        val emptyContent = ""

        val parser = XmlParser.instance
        val result = parser.parse(emptyContent)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `should gracefully handle whitespace-only files`() {
        val whitespaceOnly = "   \n\n  \t  "

        val parser = XmlParser.instance
        val result = parser.parse(whitespaceOnly)

        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `should handle truncated XML gracefully`() {
        val truncatedXml = """<?xml version="1.0" encoding="UTF-8"?>
<root>
    <element>Incomplete"""

        val parser = XmlParser.instance
        val result = parser.parse(truncatedXml)

        assertTrue(result is ParseResult.Failure)
    }
}
