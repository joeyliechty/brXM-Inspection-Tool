package org.bloomreach.inspections.cli.commands

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertTrue

class ListInspectionsCommandTest {

    @Test
    fun `should list all inspections`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Verify output structure
            assertTrue(output.contains("Bloomreach CMS Inspections"))
            assertTrue(output.contains("Total inspections:"))
            assertTrue(output.contains("Statistics:"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should display inspections grouped by category`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should show at least some categories
            assertTrue(
                output.contains("Repository Tier") ||
                output.contains("Configuration") ||
                output.contains("Performance") ||
                output.contains("Security")
            )
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should show severity levels with icons`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should display severity levels
            assertTrue(
                output.contains("ERROR") ||
                output.contains("WARNING") ||
                output.contains("INFO") ||
                output.contains("HINT")
            )
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should display inspection statistics`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Verify statistics section
            assertTrue(output.contains("By Severity:") || output.contains("Statistics:"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should include file type statistics`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // File type stats should be present
            assertTrue(output.contains("By File Type:") || output.contains("JAVA") || output.contains("XML"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should not throw exceptions during execution`() {
        val command = ListInspectionsCommand()

        // Should not throw
        command.run()
    }

    @Test
    fun `should display meaningful content with multiple inspections`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()
            val lines = output.split("\n")

            // Should have substantial output
            assertTrue(lines.size > 20, "Output should have multiple lines showing inspections")
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `should display inspection descriptions`() {
        val command = ListInspectionsCommand()

        val originalOut = System.out
        val capturedOut = ByteArrayOutputStream()
        System.setOut(PrintStream(capturedOut))

        try {
            command.run()
            val output = capturedOut.toString()

            // Should contain inspection names and descriptions
            assertTrue(output.contains("[") && output.contains("]"))
        } finally {
            System.setOut(originalOut)
        }
    }
}
