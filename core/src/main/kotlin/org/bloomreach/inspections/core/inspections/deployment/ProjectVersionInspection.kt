package org.bloomreach.inspections.core.inspections.deployment

import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.xml.XmlParser
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Detects projects with version numbers that have never been updated from defaults
 * or are at very low version numbers, suggesting improper semantic versioning practices.
 *
 * This is an informational suggestion that projects should use proper semantic versioning
 * as they mature. Development-time versions like 0.1.0-SNAPSHOT are reasonable for new
 * projects, but should be updated as the project evolves.
 *
 * Common issues:
 * - Project never incremented version from initial 0.1.0-SNAPSHOT
 * - Version stuck at 0.0.x after months of development
 * - No version strategy in place
 * - Missing semantic versioning understanding
 *
 * Best practice: Use semantic versioning (MAJOR.MINOR.PATCH) in production releases.
 * Development versions (0.x.x-SNAPSHOT) are fine during active development.
 */
class ProjectVersionInspection : Inspection() {
    override val id = "deployment.project-version"
    override val name = "Project Version Needs Update"
    override val description = """
        Suggests projects use proper semantic versioning as they mature.

        This inspection checks pom.xml files for:
        - Versions stuck at default 0.1.0-SNAPSHOT
        - Very low version numbers (0.0.x or 0.1.x) that suggest no versioning strategy
        - Lack of semantic versioning (MAJOR.MINOR.PATCH)

        While development-time SNAPSHOT versions are perfectly fine for active development,
        they should be updated to reflect the project's maturity and release schedule.

        Learn more: https://semver.org/
    """.trimIndent()
    override val category = InspectionCategory.DEPLOYMENT
    override val severity = Severity.HINT
    override val applicableFileTypes = setOf(FileType.XML)

    // Default initial version in Maven archetypes
    private val defaultVersion = "0.1.0-SNAPSHOT"

    // Very low version numbers that suggest no versioning was done
    private val lowVersionPatterns = Regex("^0\\.(0|1)\\.[0-9]")

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        // Only check pom.xml files
        if (!context.file.name.equals("pom.xml", ignoreCase = true)) {
            return emptyList()
        }

        val xmlParser = XmlParser.instance
        val parseResult = xmlParser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val doc = parseResult.ast
        val version = extractVersion(doc)

        if (version == null) {
            return emptyList()
        }

        val issues = mutableListOf<InspectionIssue>()

        // Check if version is the default
        if (version == defaultVersion) {
            issues.add(createDefaultVersionIssue(version, context))
        }
        // Check if version is a low version number
        else if (lowVersionPatterns.containsMatchIn(version) && !version.endsWith("-SNAPSHOT")) {
            // Production version at 0.0.x or 0.1.x is unusual without good reason
            issues.add(createLowProductionVersionIssue(version, context))
        }

        return issues
    }

    /**
     * Extract version from pom.xml
     * Must get the direct child <version> of <project>, not <project><parent><version>
     */
    private fun extractVersion(doc: Document): String? {
        val rootElement = doc.documentElement

        // Iterate through direct children of project element
        val children = rootElement.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element

                // Skip parent element - we want the version after it
                if (element.tagName == "parent") {
                    continue
                }

                // Once we're past parent, get the first version element
                if (element.tagName == "version") {
                    val version = element.textContent?.trim()
                    if (!version.isNullOrEmpty()) {
                        return version
                    }
                }
            }
        }

        return null
    }

    /**
     * Create issue when version is still at default
     */
    private fun createDefaultVersionIssue(version: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Project version still at default '$version'",
            description = """
                Your project version is still at the default value '$version'.

                This typically indicates the project version has never been updated since project
                initialization. While development-time SNAPSHOT versions are fine during active
                development, you should establish a versioning strategy as your project matures.

                **Semantic Versioning Guidelines (SemVer)**:

                Format: `MAJOR.MINOR.PATCH[-PRERELEASE]`

                Example progression:
                - `0.1.0-SNAPSHOT` - Initial development (current)
                - `0.1.0` - First alpha/development release
                - `0.2.0` - Second iteration with new features
                - `1.0.0` - Production-ready release (increment MAJOR)
                - `1.0.1` - Bug fix (increment PATCH)
                - `1.1.0` - New feature (increment MINOR)
                - `2.0.0` - Breaking change (increment MAJOR)

                **SNAPSHOT vs Release**:
                - `X.Y.Z-SNAPSHOT` - Development version (can change at any time)
                - `X.Y.Z` - Release version (immutable, for production)

                **For Bloomreach Projects**:
                Bloomreach follows this pattern:
                - `14.0.0-SNAPSHOT` - Development version during iteration
                - `14.0.0` - Release version
                - `14.1.0-SNAPSHOT` - Next version development

                **When to Update Your Version**:
                - Before major releases to production (1.0.0)
                - When adding significant new features (0.2.0, 1.1.0)
                - When making breaking changes (2.0.0)
                - When fixing bugs (1.0.1)

                **Benefits of Proper Versioning**:
                - Clear communication of changes to users
                - Dependency management becomes predictable
                - Release notes aligned with versions
                - Historical tracking of changes
                - CI/CD pipeline integration

                **How to Update in Maven**:
                ```bash
                # Update version in pom.xml
                mvn versions:set -DnewVersion=1.0.0

                # Or manually edit pom.xml:
                # <version>1.0.0</version>
                ```

                **References**:
                - [Semantic Versioning](https://semver.org/)
                - [Maven Versioning Guide](https://maven.apache.org/guides/introduction/introduction-to-versioning.html)
                - [Bloomreach Release Notes](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("version" to version, "reason" to "default")
        )
    }

    /**
     * Create issue for low production version
     */
    private fun createLowProductionVersionIssue(version: String, context: InspectionContext): InspectionIssue {
        return InspectionIssue(
            inspection = this,
            file = context.file,
            severity = severity,
            message = "Project version is very low: '$version'",
            description = """
                Your project version is at `$version`, which appears to be a very low version
                number for a production release.

                Production versions at `0.0.x` or `0.1.x` suggest:
                - Minimal or no versioning strategy in place
                - Project may not have been released or updated properly
                - Lack of semantic versioning understanding
                - Possible oversight during release process

                **Semantic Versioning Guidelines**:

                Format: `MAJOR.MINOR.PATCH`

                - `MAJOR` (first number) - Increment for breaking changes
                - `MINOR` (second number) - Increment for new features (backwards compatible)
                - `PATCH` (third number) - Increment for bug fixes

                **Version Progression Example**:
                ```
                0.1.0-SNAPSHOT  → Initial development
                0.1.0           → First release
                0.2.0           → Added features
                0.3.0           → More features
                1.0.0           → Production-ready (breaking changes)
                1.0.1           → Bug fix
                1.1.0           → New feature
                2.0.0           → Major breaking change
                ```

                **When to Use 0.x.x**:
                - Early development/alpha versions
                - Unstable APIs that may change
                - Pre-release development versions

                **When to Use 1.x.x**:
                - Stable API
                - Production-ready code
                - Backward compatibility (within MINOR version)

                **Current Version Assessment**:
                Your version `$version` suggests either:
                1. Project is in early development (which is fine with -SNAPSHOT)
                2. Version was never properly incremented during releases
                3. Versioning strategy is unclear

                **Recommendation**:
                If your project has been in use and is stable, consider incrementing to `1.0.0`
                or higher. If still in active development, use `-SNAPSHOT` suffix.

                **How to Update**:
                ```bash
                # Using Maven
                mvn versions:set -DnewVersion=1.0.0

                # Or edit pom.xml directly
                ```

                **References**:
                - [Semantic Versioning Spec](https://semver.org/)
                - [Maven Version Format](https://maven.apache.org/guides/introduction/introduction-to-versioning.html)
                - [Bloomreach Versions](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = TextRange.wholeLine(1),
            metadata = mapOf("version" to version, "reason" to "low-version")
        )
    }
}
