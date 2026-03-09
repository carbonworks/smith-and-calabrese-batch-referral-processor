package tech.carbonworks.snc.batchreferralparser.output

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.ServiceLine
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [CsvWriter] covering CSV output generation.
 *
 * Uses JUnit 5 [TempDir] for isolated file system access. Each test writes
 * a CSV file and reads it back to verify content.
 */
class CsvWriterTest {

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Read all lines from a CSV file. */
    private fun readLines(file: File): List<String> = file.readLines(Charsets.UTF_8)

    /** Build a fully-populated [ReferralFields] with known test values. */
    private fun sampleReferral(
        firstName: String = "Jane",
        middleName: String = "M",
        lastName: String = "Doe",
    ): ReferralFields {
        return ReferralFields(
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            caseId = "CASE-001",
            authorizationNumber = "AUTH-12345",
            requestId = "REQ-999",
            dateOfIssue = "02/01/2026",
            dob = "05/15/1990",
            applicantName = "John Doe",
            appointmentDate = "03/01/2026",
            appointmentTime = "10:00 AM",
            streetAddress = "123 Main St",
            city = "Anytown",
            state = "CA",
            zipCode = "90210",
            phone = "555-123-4567",
            services = listOf(
                ServiceLine(cptCode = "96130", description = "Psych eval"),
                ServiceLine(cptCode = "96131", description = "Add'l hour"),
            ),
            federalTaxId = "12-3456789",
            vendorNumber = "V-1234",
            caseNumberFullFooter = "FULL-CASE-001",
            assignedCode = "AC-01",
            dccNumber = "DCC-555",
        )
    }

    private val fixedTimestamp = LocalDateTime.of(2026, 2, 23, 14, 30, 45)

    // -------------------------------------------------------------------
    // Test 1: Filename matches authorizations-*.csv pattern
    // -------------------------------------------------------------------

    @Test
    fun `filename matches authorizations csv pattern`() {
        val file = CsvWriter.write(emptyList(), tempDir, fixedTimestamp)

        assertEquals("authorizations-2026-02-23-143045.csv", file.name)
        assertTrue(file.name.matches(Regex("authorizations-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.csv")))
        assertTrue(file.exists())
    }

    // -------------------------------------------------------------------
    // Test 2: Empty list produces headers-only CSV
    // -------------------------------------------------------------------

    @Test
    fun `empty list produces headers-only csv`() {
        val file = CsvWriter.write(emptyList(), tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Should have exactly one line (the header)
        assertEquals(1, lines.size)

        // First field should be "First Name"
        assertTrue(lines[0].startsWith("First Name,"))
    }

    // -------------------------------------------------------------------
    // Test 3: Single referral produces correct data
    // -------------------------------------------------------------------

    @Test
    fun `single referral produces header and one data row`() {
        val referral = sampleReferral()
        val file = CsvWriter.write(listOf(referral), tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Header + 1 data row
        assertEquals(2, lines.size)

        // Parse header columns
        val headers = parseCsvLine(lines[0])
        assertEquals("First Name", headers[0])
        assertEquals("Middle Name", headers[1])
        assertEquals("Last Name", headers[2])

        // Parse data row
        val data = parseCsvLine(lines[1])
        assertEquals("Jane", data[0])
        assertEquals("M", data[1])
        assertEquals("Doe", data[2])
        assertEquals("CASE-001", data[3])
    }

    // -------------------------------------------------------------------
    // Test 4: Multiple referrals produce multiple rows
    // -------------------------------------------------------------------

    @Test
    fun `multiple referrals produce multiple rows`() {
        val referrals = listOf(
            sampleReferral(firstName = "Alice"),
            sampleReferral(firstName = "Bob"),
            sampleReferral(firstName = "Carol"),
        )
        val file = CsvWriter.write(referrals, tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Header + 3 data rows
        assertEquals(4, lines.size)
    }

    // -------------------------------------------------------------------
    // Test 5: RFC 4180 escaping — commas in values
    // -------------------------------------------------------------------

    @Test
    fun `field with comma is quoted`() {
        assertEquals("\"hello, world\"", CsvWriter.escapeField("hello, world"))
    }

    // -------------------------------------------------------------------
    // Test 6: RFC 4180 escaping — double quotes in values
    // -------------------------------------------------------------------

    @Test
    fun `field with double quotes is escaped`() {
        assertEquals("\"say \"\"hello\"\"\"", CsvWriter.escapeField("say \"hello\""))
    }

    // -------------------------------------------------------------------
    // Test 7: RFC 4180 escaping — newlines in values
    // -------------------------------------------------------------------

    @Test
    fun `field with newline is quoted`() {
        assertEquals("\"line1\nline2\"", CsvWriter.escapeField("line1\nline2"))
    }

    // -------------------------------------------------------------------
    // Test 8: Plain field is not quoted
    // -------------------------------------------------------------------

    @Test
    fun `plain field is not quoted`() {
        assertEquals("hello", CsvWriter.escapeField("hello"))
    }

    // -------------------------------------------------------------------
    // Test 9: Empty field produces empty string
    // -------------------------------------------------------------------

    @Test
    fun `empty field produces empty string`() {
        assertEquals("", CsvWriter.escapeField(""))
    }

    // -------------------------------------------------------------------
    // Test 10: Dates are written as plain text in CSV
    // -------------------------------------------------------------------

    @Test
    fun `dates are written as plain text strings`() {
        val referral = ReferralFields(
            firstName = "Test",
            dateOfIssue = "August 13, 2024",
            dob = "09/15/1990",
        )
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "dateOfIssue", displayName = "Date of Issue"),
                ExportColumn.Field(fieldId = "dob", displayName = "DOB"),
            ),
        )

        val file = CsvWriter.write(listOf(referral), tempDir, fixedTimestamp, columnConfig = config)
        val lines = readLines(file)
        val data = parseCsvLine(lines[1])

        // Dates should be plain text, not converted
        assertEquals("Test", data[0])
        assertEquals("August 13, 2024", data[1])
        assertEquals("09/15/1990", data[2])
    }

    // -------------------------------------------------------------------
    // Test 11: Custom column config works with CSV
    // -------------------------------------------------------------------

    @Test
    fun `custom column order works correctly`() {
        val referral = sampleReferral()
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "caseId", displayName = "Case ID"),
            ),
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = parseCsvLine(lines[0])
        assertEquals("Last Name", headers[0])
        assertEquals("First Name", headers[1])
        assertEquals("Case ID", headers[2])

        val data = parseCsvLine(lines[1])
        assertEquals("Doe", data[0])
        assertEquals("Jane", data[1])
        assertEquals("CASE-001", data[2])
    }

    // -------------------------------------------------------------------
    // Test 12: Disabled fields are excluded
    // -------------------------------------------------------------------

    @Test
    fun `disabled fields are excluded from csv`() {
        val referral = sampleReferral()
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "middleName", displayName = "Middle Name", enabled = false),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
            ),
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = parseCsvLine(lines[0])
        assertEquals(2, headers.size)
        assertEquals("First Name", headers[0])
        assertEquals("Last Name", headers[1])
    }

    // -------------------------------------------------------------------
    // Test 13: Spacer columns produce empty fields
    // -------------------------------------------------------------------

    @Test
    fun `spacer columns produce empty fields`() {
        val referral = sampleReferral()
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Spacer(id = "spacer_1"),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
            ),
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = parseCsvLine(lines[0])
        assertEquals(3, headers.size)
        assertEquals("First Name", headers[0])
        assertEquals("", headers[1])
        assertEquals("Last Name", headers[2])

        val data = parseCsvLine(lines[1])
        assertEquals("Jane", data[0])
        assertEquals("", data[1])
        assertEquals("Doe", data[2])
    }

    // -------------------------------------------------------------------
    // Test 14: expandServices produces one row per service
    // -------------------------------------------------------------------

    @Test
    fun `expandServices produces one row per service`() {
        val referral = sampleReferral()
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = true,
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )
        val lines = readLines(file)

        // Header + 2 data rows (one per service)
        assertEquals(3, lines.size)

        val row1 = parseCsvLine(lines[1])
        assertEquals("Jane", row1[0])
        assertEquals("96130", row1[1])

        val row2 = parseCsvLine(lines[2])
        assertEquals("Jane", row2[0])
        assertEquals("96131", row2[1])
    }

    // -------------------------------------------------------------------
    // Test 15: Services column flattens CPT codes when not expanded
    // -------------------------------------------------------------------

    @Test
    fun `services flattened to comma-separated when not expanded`() {
        val referral = sampleReferral()
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = false,
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )
        val lines = readLines(file)

        // Header + 1 data row
        assertEquals(2, lines.size)

        val data = parseCsvLine(lines[1])
        assertEquals("Jane", data[0])
        // Comma in services value should be quoted
        assertEquals("96130, 96131", data[1])
    }

    // -------------------------------------------------------------------
    // Test 16: File uses CRLF line endings per RFC 4180
    // -------------------------------------------------------------------

    @Test
    fun `file uses CRLF line endings`() {
        val referral = sampleReferral()
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
            ),
        )

        val file = CsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )

        val rawContent = file.readBytes().toString(Charsets.UTF_8)
        assertTrue(rawContent.contains("\r\n"), "CSV should use CRLF line endings")
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Simple CSV line parser that handles RFC 4180 quoting.
     * Handles quoted fields with commas, doubled quotes, etc.
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        var i = 0
        while (i <= line.length) {
            if (i == line.length) {
                // Trailing empty field after final comma
                break
            }
            if (line[i] == '"') {
                // Quoted field
                val sb = StringBuilder()
                i++ // skip opening quote
                while (i < line.length) {
                    if (line[i] == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            sb.append('"')
                            i += 2
                        } else {
                            i++ // skip closing quote
                            break
                        }
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                fields.add(sb.toString())
                if (i < line.length && line[i] == ',') i++ // skip comma
            } else {
                // Unquoted field
                val commaIdx = line.indexOf(',', i)
                if (commaIdx == -1) {
                    fields.add(line.substring(i))
                    i = line.length
                } else {
                    fields.add(line.substring(i, commaIdx))
                    i = commaIdx + 1
                    // If comma is at the end, add empty trailing field
                    if (i == line.length) {
                        fields.add("")
                    }
                }
            }
        }
        return fields
    }
}
