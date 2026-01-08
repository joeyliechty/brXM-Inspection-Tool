package org.bloomreach.inspections.core.inspections.security

import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.MethodCallExpr
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects XML parsers that don't disable external entity processing (XXE vulnerability).
 *
 * XXE (XML External Entity) vulnerabilities allow attackers to:
 * - Read arbitrary files from the server
 * - Perform SSRF attacks
 * - Cause denial of service through billion laughs attack
 *
 * From community analysis: XXE vulnerabilities found in content import/export features.
 */
class XmlExternalEntityInspection : Inspection() {
    override val id = "security.xxe-external-entity"
    override val name = "XML External Entity (XXE) Vulnerability"
    override val description = """
        Detects XML parsers without XXE (External Entity) protection.

        XXE vulnerabilities allow attackers to:
        1. Read arbitrary files from the server (e.g., /etc/passwd, config files)
        2. Perform Server-Side Request Forgery (SSRF) attacks
        3. Cause denial of service through billion laughs attack
        4. Access internal network resources

        This inspection checks for unprotected XML parser creation:
        - DocumentBuilderFactory.newInstance()
        - XMLInputFactory.newInstance()
        - SAXParserFactory.newInstance()

        These must have XXE protection features configured before use.
    """.trimIndent()
    override val category = InspectionCategory.SECURITY
    override val severity = Severity.ERROR
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Skip test files (often contain XXE for testing purposes)
        if (isTestFile(context)) {
            return emptyList()
        }

        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = XxeVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    private fun isTestFile(context: InspectionContext): Boolean {
        val path = context.file.path.toString().lowercase()
        return path.contains("/test/") ||
               path.contains("\\test\\") ||
               path.endsWith("test.java") ||
               path.endsWith("test.kt")
    }

    fun buildDescription(factoryType: String): String {
        return """
            XXE vulnerability detected: $factoryType created without XXE protection.

            XML External Entity (XXE) attacks allow attackers to:
            1. Read arbitrary files: /etc/passwd, sensitive config files
            2. Perform SSRF attacks: Access internal network resources
            3. Cause DoS: Billion laughs or XML bomb attacks
            4. Exfiltrate data: Through out-of-band data channels

            To fix this vulnerability, configure XXE protection:

            Option 1: DocumentBuilderFactory (recommended)
            ```java
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(inputStream);
            ```

            Option 2: XMLInputFactory
            ```java
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            ```

            Option 3: SAXParserFactory
            ```java
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            spf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            SAXParser parser = spf.newSAXParser();
            ```

            References:
            - OWASP: https://owasp.org/www-community/XML_External_Entity_(XXE)_Prevention_Cheat_Sheet
            - CWE-611: Improper Restriction of XML External Entity Reference
        """.trimIndent()
    }
}

private class XxeVisitor(
    private val inspection: XmlExternalEntityInspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    private val xmlParserFactories = setOf(
        "DocumentBuilderFactory",
        "XMLInputFactory",
        "SAXParserFactory"
    )

    private val xxeProtectionMarkers = setOf(
        "disallow-doctype-decl",
        "external-general-entities",
        "external-parameter-entities",
        "FEATURE_SECURE_PROCESSING",
        "ACCESS_EXTERNAL_DTD",
        "ACCESS_EXTERNAL_SCHEMA"
    )

    override fun visit(variable: VariableDeclarator, ctx: InspectionContext) {
        super.visit(variable, ctx)

        // Check if initializer is a method call
        variable.initializer.ifPresent { init ->
            if (init !is MethodCallExpr) {
                return@ifPresent
            }

            val methodCall = init as MethodCallExpr

            // Check for newInstance() method call
            if (methodCall.nameAsString != "newInstance") {
                return@ifPresent
            }

            // Check if the scope is an XML parser factory
            val scope = methodCall.scope.orElse(null)?.toString() ?: return@ifPresent

            // Determine which factory type
            val factoryType = xmlParserFactories.firstOrNull { factory ->
                scope == factory || scope.contains(factory)
            } ?: return@ifPresent

            // Check if file contains XXE protection
            val hasXxeProtection = context.fileContent.split("\n").any { line ->
                xxeProtectionMarkers.any { marker -> line.contains(marker) }
            }

            if (!hasXxeProtection) {
                val line = methodCall.begin.map { it.line }.orElse(0)
                issues.add(
                    InspectionIssue(
                        inspection = inspection,
                        file = context.file,
                        severity = inspection.severity,
                        message = "$factoryType created without XXE protection",
                        description = inspection.buildDescription(factoryType),
                        range = TextRange.wholeLine(line),
                        metadata = mapOf(
                            "parserType" to factoryType,
                            "line" to line
                        )
                    )
                )
            }
        }
    }
}
