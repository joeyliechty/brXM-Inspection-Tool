package org.bloomreach.inspections.core.inspections.config

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.IfStmt
import org.bloomreach.inspections.core.engine.*
import org.bloomreach.inspections.core.parsers.ParseResult
import org.bloomreach.inspections.core.parsers.java.JavaAstVisitor
import org.bloomreach.inspections.core.parsers.java.JavaParser

/**
 * Detects HST component parameters accessed without null checks.
 *
 * This is a common issue (20% of configuration problems) causing NullPointerExceptions.
 *
 * Problem: Component parameters from repository configuration can be null if:
 * - Parameter not configured in CMS
 * - Typo in parameter name
 * - Component moved to different context
 * - Configuration not yet published
 *
 * Best practice: Always null-check parameter values before use.
 */
class ComponentParameterNullInspection : Inspection() {
    override val id = "config.component-parameter-null"
    override val name = "Component Parameter Null Check"
    override val description = """
        Detects HST component parameters accessed without null checks.
        Component parameters should always be validated before use to prevent NullPointerException.
    """.trimIndent()
    override val category = InspectionCategory.CONFIGURATION
    override val severity = Severity.WARNING
    override val applicableFileTypes = setOf(FileType.JAVA)

    override fun inspect(context: InspectionContext): List<InspectionIssue> {
        val parser = JavaParser.instance
        val parseResult = parser.parse(context.fileContent)

        if (parseResult !is ParseResult.Success) {
            return emptyList()
        }

        val ast = parseResult.ast
        val visitor = ParameterVisitor(this, context)

        return visitor.visitAndGetIssues(ast, context)
    }

    override fun getQuickFixes(issue: InspectionIssue): List<QuickFix> {
        return listOf(
            AddNullCheckQuickFix()
        )
    }
}

/**
 * Visitor that detects unchecked parameter access
 */
private class ParameterVisitor(
    private val inspection: Inspection,
    private val context: InspectionContext
) : JavaAstVisitor() {

    // Track variables assigned from getParameter()
    private val parameterVariables = mutableMapOf<String, ParameterAccess>()

    // Known parameter access methods
    private val parameterMethods = setOf(
        "getParameter",
        "getPublicRequestParameter",
        "getComponentParameter"
    )

    override fun visit(method: MethodDeclaration, ctx: InspectionContext) {
        // Clear tracking for each method
        parameterVariables.clear()

        // Visit method body
        super.visit(method, ctx)

        // Check all parameter accesses found in this method
        parameterVariables.values.forEach { access ->
            if (!access.hasNullCheck && access.isUsed) {
                issues.add(createUncheckedParameterIssue(access))
            }
        }
    }

    override fun visit(call: MethodCallExpr, ctx: InspectionContext) {
        super.visit(call, ctx)

        // Check if this is a parameter access call
        if (call.nameAsString in parameterMethods) {
            handleParameterAccess(call)
        }

        // Check if this call uses a parameter variable as scope/receiver
        call.scope.ifPresent { scope ->
            if (scope is NameExpr) {
                val varName = scope.nameAsString
                if (parameterVariables.containsKey(varName)) {
                    parameterVariables[varName]?.isUsed = true
                }
            }
        }

        // Check if this call uses a parameter variable as argument
        call.arguments.forEach { arg ->
            if (arg is NameExpr) {
                val varName = arg.nameAsString
                if (parameterVariables.containsKey(varName)) {
                    parameterVariables[varName]?.isUsed = true
                }
            }
        }
    }

    override fun visit(fieldAccess: FieldAccessExpr, ctx: InspectionContext) {
        super.visit(fieldAccess, ctx)

        // Check if the scope of field access is a parameter variable
        val scope = fieldAccess.scope
        if (scope is NameExpr) {
            val varName = scope.nameAsString
            if (parameterVariables.containsKey(varName)) {
                parameterVariables[varName]?.isUsed = true
            }
        }
    }

    override fun visit(ifStmt: IfStmt, ctx: InspectionContext) {
        super.visit(ifStmt, ctx)

        // Check if this if statement is a null check for a parameter variable
        val condition = ifStmt.condition.toString()

        parameterVariables.keys.forEach { varName ->
            if (condition.contains("$varName != null") ||
                condition.contains("null != $varName") ||
                condition.contains("$varName == null") ||
                condition.contains("null == $varName")) {
                parameterVariables[varName]?.hasNullCheck = true
            }
        }
    }

    private fun handleParameterAccess(call: MethodCallExpr) {
        // Check if the result is assigned to a variable
        val parent = call.parentNode.orElse(null)

        if (parent != null) {
            val assignment = findVariableAssignment(parent)
            if (assignment != null) {
                val parameterName = extractParameterName(call)
                parameterVariables[assignment] = ParameterAccess(
                    variableName = assignment,
                    parameterName = parameterName,
                    callExpression = call,
                    hasNullCheck = false,
                    isUsed = false
                )
            } else {
                // Inline usage without assignment - check if it's inside a null check condition
                val parameterName = extractParameterName(call)

                // Check if this call is inside an if condition that checks for null
                if (!isInsideNullCheckCondition(call, parameterName)) {
                    issues.add(createInlineParameterIssue(call, parameterName))
                }
            }
        }
    }

    /**
     * Check if a call is inside an if condition that checks the parameter for null
     */
    private fun isInsideNullCheckCondition(call: MethodCallExpr, parameterName: String): Boolean {
        var current: com.github.javaparser.ast.Node? = call.parentNode.orElse(null)

        while (current != null) {
            if (current is IfStmt) {
                // Check if the condition checks for null on this parameter
                val condition = current.condition.toString()

                // Pattern 1: parameter != null or parameter == null
                if (condition.contains("getParameter(\"$parameterName\")") &&
                    (condition.contains("!= null") || condition.contains("== null") ||
                     condition.contains("null !=") || condition.contains("null =="))) {
                    return true
                }

                // Pattern 2: object.getParameter("param") != null
                val pattern = Regex("""[\w.]*\.getParameter\s*\(\s*"$parameterName"\s*\)\s*(!|==|!=)\s*null|null\s*(!|==|!=)\s*[\w.]*\.getParameter\s*\(\s*"$parameterName"\s*\)""")
                if (pattern.containsMatchIn(condition)) {
                    return true
                }

                // If we've reached an if block and the condition doesn't check this parameter,
                // and the call is directly inside the then block, we're safe
                // But if it's in a nested block, we need to keep checking
            }

            current = current.parentNode.orElse(null)
        }

        return false
    }

    private fun findVariableAssignment(node: com.github.javaparser.ast.Node): String? {
        return when (node) {
            is com.github.javaparser.ast.body.VariableDeclarator -> node.nameAsString
            is com.github.javaparser.ast.expr.VariableDeclarationExpr -> {
                node.variables.firstOrNull()?.nameAsString
            }
            is com.github.javaparser.ast.expr.AssignExpr -> {
                node.target.toString()
            }
            else -> null
        }
    }

    private fun extractParameterName(call: MethodCallExpr): String {
        return if (call.arguments.isNotEmpty()) {
            call.arguments[0].toString().trim('"')
        } else {
            "unknown"
        }
    }

    private fun createUncheckedParameterIssue(access: ParameterAccess): InspectionIssue {
        val range = access.callExpression.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = inspection.severity,
            message = "Parameter '${access.parameterName}' used without null check",
            description = """
                The component parameter '${access.parameterName}' is accessed via ${access.callExpression.nameAsString}()
                and stored in variable '${access.variableName}', but used without null checking.

                **Risk**: HIGH - NullPointerException if parameter not configured

                **Why This Happens**:
                - Parameter not configured in CMS UI
                - Typo in parameter name
                - Component used in different context without configuration
                - Configuration not published yet
                - Parameter removed during refactoring

                **Problem Pattern**:
                ```java
                // ⚠️ PROBLEM - No null check
                String value = getParameter("myParam");
                result.setText(value.toUpperCase()); // NPE if value is null!
                ```

                **Correct Patterns**:

                **1. Simple Null Check** (Recommended):
                ```java
                String value = getParameter("myParam");
                if (value != null) {
                    result.setText(value.toUpperCase());
                } else {
                    // Provide default or skip
                    result.setText("Default Value");
                }
                ```

                **2. Early Return**:
                ```java
                String value = getParameter("myParam");
                if (value == null) {
                    logger.warn("Parameter 'myParam' not configured");
                    return;
                }
                result.setText(value.toUpperCase());
                ```

                **3. Default Value**:
                ```java
                String value = getParameter("myParam");
                String safeValue = (value != null) ? value : "defaultValue";
                result.setText(safeValue.toUpperCase());
                ```

                **4. Optional Pattern** (Modern Java):
                ```java
                Optional.ofNullable(getParameter("myParam"))
                    .map(String::toUpperCase)
                    .ifPresent(result::setText);
                ```

                **5. Apache Commons** (if available):
                ```java
                String value = StringUtils.defaultString(getParameter("myParam"), "default");
                result.setText(value.toUpperCase());
                ```

                **Best Practices**:
                - Document required parameters in component class JavaDoc
                - Log warnings when required parameters are missing
                - Provide sensible defaults where possible
                - Use parameter validation in doBeforeRender()
                - Consider creating a ParametersInfo class with validated parameters

                **Example: Robust Parameter Handling**:
                ```java
                @Override
                public void doBeforeRender(HstRequest request, HstResponse response) {
                    String title = getParameter("title");
                    String limit = getParameter("limit");

                    // Validate required parameters
                    if (title == null) {
                        logger.warn("Required parameter 'title' not configured for component {}",
                            request.getRequestContext().getResolvedSiteMapItem().getHstComponentConfiguration().getName());
                        request.setAttribute("error", "Component not properly configured");
                        return;
                    }

                    // Optional parameter with default
                    int maxItems = 10;
                    if (limit != null) {
                        try {
                            maxItems = Integer.parseInt(limit);
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid limit parameter: {}", limit);
                        }
                    }

                    request.setAttribute("title", title);
                    request.setAttribute("maxItems", maxItems);
                }
                ```

                **Common HST Parameters to Check**:
                - Document picker paths
                - Query parameters
                - Display settings (limit, offset)
                - Configuration flags
                - Custom component settings

                **Related Community Issues**:
                - NullPointerException in component rendering
                - "Component not displaying" (due to NPE in doBeforeRender)
                - Parameters working locally but not on server

                **HST Component Configuration**:
                Component parameters are configured in repository at:
                ```
                /hst:hst/hst:configurations/[project]/hst:components/[component]
                  + hst:parameternames = ['param1', 'param2']
                  + hst:parametervalues = ['value1', 'value2']
                ```

                **References**:
                - [HST Component Development](https://xmdocumentation.bloomreach.com/)
                - [Component Parameters Guide](https://xmdocumentation.bloomreach.com/)
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "variableName" to access.variableName,
                "parameterName" to access.parameterName,
                "methodName" to access.callExpression.nameAsString
            )
        )
    }

    private fun createInlineParameterIssue(call: MethodCallExpr, parameterName: String): InspectionIssue {
        val range = call.range.map { r ->
            TextRange(
                startLine = r.begin.line,
                startColumn = r.begin.column,
                endLine = r.end.line,
                endColumn = r.end.column
            )
        }.orElse(TextRange.wholeLine(1))

        return InspectionIssue(
            inspection = inspection,
            file = context.file,
            severity = Severity.ERROR, // More severe - immediate NPE risk
            message = "Parameter '$parameterName' used inline without null check",
            description = """
                The component parameter '$parameterName' is accessed and used immediately
                without any null checking. This will cause a NullPointerException if the
                parameter is not configured.

                **Problem**: Inline usage without assignment makes it impossible to null-check

                **Example**:
                ```java
                // ❌ PROBLEM - Inline usage
                result.setText(getParameter("title").toUpperCase());
                // NPE if 'title' parameter not configured!
                ```

                **Solution**: Assign to variable first, then null-check:
                ```java
                String title = getParameter("title");
                if (title != null) {
                    result.setText(title.toUpperCase());
                } else {
                    result.setText("Untitled");
                }
                ```

                See the main documentation for comprehensive examples and best practices.
            """.trimIndent(),
            range = range,
            metadata = mapOf(
                "parameterName" to parameterName,
                "methodName" to call.nameAsString,
                "inlineUsage" to true
            )
        )
    }
}

/**
 * Information about a parameter access
 */
private data class ParameterAccess(
    val variableName: String,
    val parameterName: String,
    val callExpression: MethodCallExpr,
    var hasNullCheck: Boolean,
    var isUsed: Boolean
)

/**
 * Quick fix: Add null check
 */
private class AddNullCheckQuickFix : BaseQuickFix(
    name = "Add null check",
    description = "Wraps parameter usage in null check"
) {
    override fun apply(context: QuickFixContext) {
        val variableName = context.issue.metadata["variableName"] as? String ?: return
        val parameterName = context.issue.metadata["parameterName"] as? String ?: return

        val content = context.file.readText()
        val lines = content.split("\n").toMutableList()

        // Find the line where the parameter is declared
        val declarationLine = context.range.startLine - 1
        if (declarationLine < 0 || declarationLine >= lines.size) return

        val line = lines[declarationLine]
        val indent = line.takeWhile { it.isWhitespace() }

        // Find the first usage of the variable after declaration
        var usageLine = declarationLine + 1
        for (i in (declarationLine + 1) until lines.size) {
            if (lines[i].contains(variableName)) {
                usageLine = i
                break
            }
        }

        // Insert null check after the declaration
        val nullCheck = """
${indent}if ($variableName != null) {
${indent}    // Use $variableName here
""".trimIndent()

        // Find where to insert the closing brace
        var closingBraceLine = usageLine + 1
        var braceCount = 1  // Already opened one brace
        for (i in (usageLine + 1) until lines.size) {
            braceCount += lines[i].count { it == '{' }
            braceCount -= lines[i].count { it == '}' }

            if (braceCount <= 0) {
                closingBraceLine = i
                break
            }
        }

        // Insert the null check
        lines.add(declarationLine + 1, nullCheck)

        // Insert the closing brace and else clause
        val elseClause = """
${indent}} else {
${indent}    // Handle null parameter
${indent}    logger.warn("Parameter '$parameterName' is null");
${indent}}
""".trimIndent()

        lines.add(closingBraceLine + 1, elseClause)

        // Write back to file
        val newContent = lines.joinToString("\n")
        java.nio.file.Files.writeString(context.file.path, newContent)
    }
}
