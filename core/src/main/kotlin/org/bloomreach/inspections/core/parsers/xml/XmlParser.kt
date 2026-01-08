package org.bloomreach.inspections.core.parsers.xml

import org.bloomreach.inspections.core.engine.FileType
import org.bloomreach.inspections.core.parsers.ParseError
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.Parser
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser for XML files using DOM.
 * Thread-safe: Creates a new DocumentBuilder for each parse operation.
 */
class XmlParser : Parser<Document> {
    private val logger = LoggerFactory.getLogger(XmlParser::class.java)

    // Thread-safe: DocumentBuilderFactory is thread-safe, DocumentBuilder is not
    private val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isValidating = false
        isIgnoringComments = false
        isIgnoringElementContentWhitespace = false
        // Allow DOCTYPE declarations but prevent XXE attacks by disabling external entity resolution
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

    override fun parse(content: String): ParseResult<Document> {
        // Skip parsing empty or whitespace-only files
        if (content.isBlank()) {
            return ParseResult.Failure(listOf(ParseError(0, 0, "File is empty or contains only whitespace")))
        }

        return try {
            // Create a new DocumentBuilder for each parse call (thread-safe)
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val doc = documentBuilder.parse(InputSource(StringReader(content)))
            ParseResult.Success(doc)
        } catch (e: SAXException) {
            logger.debug("Failed to parse XML: {}", e.message)
            ParseResult.Failure(listOf(ParseError(0, 0, e.message ?: "XML parse error")))
        } catch (e: Exception) {
            logger.debug("Unexpected error parsing XML: {}", e.message)
            ParseResult.Failure(listOf(ParseError(0, 0, "Unexpected error: ${e.message}")))
        }
    }

    override fun supports(fileType: FileType): Boolean {
        return fileType == FileType.XML
    }

    companion object {
        val instance = XmlParser()
    }
}

/**
 * Helper extensions for working with XML DOM
 */
fun Element.getChildElements(): List<Element> {
    val result = mutableListOf<Element>()
    val children = this.childNodes
    for (i in 0 until children.length) {
        val node = children.item(i)
        if (node is Element) {
            result.add(node)
        }
    }
    return result
}

fun Element.getChildElementsByTagName(tagName: String): List<Element> {
    return getChildElements().filter { it.tagName == tagName }
}

fun Element.getAttributeOrNull(name: String): String? {
    return if (hasAttribute(name)) getAttribute(name) else null
}
