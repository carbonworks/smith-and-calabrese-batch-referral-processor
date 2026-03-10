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
 * Tests for [TsvWriter] covering TSV output generation.
 *
 * Uses JUnit 5 [TempDir] for isolated file system access. Each test writes
 * a TSV file and reads it back to verify content.
 */
class TsvWriterTest {

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Read all lines from a TSV file. */
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
    // Test 1: Filename matches authorizations-*.tsv pattern
    // -------------------------------------------------------------------

    @Test
    fun `filename matches authorizations tsv pattern`() {
        val file = TsvWriter.write(emptyList(), tempDir, fixedTimestamp)

        assertEquals("authorizations-2026-02-23-143045.tsv", file.name)
        assertTrue(file.name.matches(Regex("authorizations-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.tsv")))
        assertTrue(file.exists())
    }

    // -------------------------------------------------------------------
    // Test 2: Empty list produces headers-only TSV
    // -------------------------------------------------------------------

    @Test
    fun `empty list produces headers-only tsv`() {
        val file = TsvWriter.write(emptyList(), tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Should have exactly one line (the header)
        assertEquals(1, lines.size)

        // First field should be "First Name"
        assertTrue(lines[0].startsWith("First Name\t"))
    }

    // -------------------------------------------------------------------
    // Test 3: Single referral produces correct data
    // -------------------------------------------------------------------

    @Test
    fun `single referral produces header and one data row`() {
        val referral = sampleReferral()
        val file = TsvWriter.write(listOf(referral), tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Header + 1 data row
        assertEquals(2, lines.size)

        // Parse header columns
        val headers = lines[0].split("\t")
        assertEquals("First Name", headers[0])
        assertEquals("Middle Name", headers[1])
        assertEquals("Last Name", headers[2])

        // Parse data row
        val data = lines[1].split("\t")
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
        val file = TsvWriter.write(referrals, tempDir, fixedTimestamp)
        val lines = readLines(file)

        // Header + 3 data rows
        assertEquals(4, lines.size)
    }

    // -------------------------------------------------------------------
    // Test 5: Tabs are stripped from field values
    // -------------------------------------------------------------------

    @Test
    fun `field with tab has tab stripped`() {
        assertEquals("helloworld", TsvWriter.escapeField("hello\tworld"))
    }

    // -------------------------------------------------------------------
    // Test 6: Newlines are stripped from field values
    // -------------------------------------------------------------------

    @Test
    fun `field with newline has newline stripped`() {
        assertEquals("line1line2", TsvWriter.escapeField("line1\nline2"))
    }

    // -------------------------------------------------------------------
    // Test 7: Carriage returns are stripped from field values
    // -------------------------------------------------------------------

    @Test
    fun `field with carriage return has carriage return stripped`() {
        assertEquals("line1line2", TsvWriter.escapeField("line1\rline2"))
    }

    // -------------------------------------------------------------------
    // Test 8: Mixed special characters are all stripped
    // -------------------------------------------------------------------

    @Test
    fun `field with mixed special characters has all stripped`() {
        assertEquals("abc", TsvWriter.escapeField("a\tb\nc"))
    }

    // -------------------------------------------------------------------
    // Test 9: Plain field is unchanged
    // -------------------------------------------------------------------

    @Test
    fun `plain field is unchanged`() {
        assertEquals("hello", TsvWriter.escapeField("hello"))
    }

    // -------------------------------------------------------------------
    // Test 10: Empty field produces empty string
    // -------------------------------------------------------------------

    @Test
    fun `empty field produces empty string`() {
        assertEquals("", TsvWriter.escapeField(""))
    }

    // -------------------------------------------------------------------
    // Test 11: Commas are NOT stripped (only tabs/newlines are)
    // -------------------------------------------------------------------

    @Test
    fun `commas are preserved in tsv fields`() {
        assertEquals("hello, world", TsvWriter.escapeField("hello, world"))
    }

    // -------------------------------------------------------------------
    // Test 12: Dates are written as plain text in TSV
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

        val file = TsvWriter.write(listOf(referral), tempDir, fixedTimestamp, columnConfig = config)
        val lines = readLines(file)
        val data = lines[1].split("\t")

        // Dates should be plain text, not converted
        assertEquals("Test", data[0])
        assertEquals("August 13, 2024", data[1])
        assertEquals("09/15/1990", data[2])
    }

    // -------------------------------------------------------------------
    // Test 13: Custom column config works with TSV
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

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = lines[0].split("\t")
        assertEquals("Last Name", headers[0])
        assertEquals("First Name", headers[1])
        assertEquals("Case ID", headers[2])

        val data = lines[1].split("\t")
        assertEquals("Doe", data[0])
        assertEquals("Jane", data[1])
        assertEquals("CASE-001", data[2])
    }

    // -------------------------------------------------------------------
    // Test 14: Disabled fields are excluded
    // -------------------------------------------------------------------

    @Test
    fun `disabled fields are excluded from tsv`() {
        val referral = sampleReferral()
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "middleName", displayName = "Middle Name", enabled = false),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
            ),
        )

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = lines[0].split("\t")
        assertEquals(2, headers.size)
        assertEquals("First Name", headers[0])
        assertEquals("Last Name", headers[1])
    }

    // -------------------------------------------------------------------
    // Test 15: Spacer columns produce empty fields
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

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )
        val lines = readLines(file)

        val headers = lines[0].split("\t")
        assertEquals(3, headers.size)
        assertEquals("First Name", headers[0])
        assertEquals("", headers[1])
        assertEquals("Last Name", headers[2])

        val data = lines[1].split("\t")
        assertEquals("Jane", data[0])
        assertEquals("", data[1])
        assertEquals("Doe", data[2])
    }

    // -------------------------------------------------------------------
    // Test 16: expandServices produces one row per service
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

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )
        val lines = readLines(file)

        // Header + 2 data rows (one per service)
        assertEquals(3, lines.size)

        val row1 = lines[1].split("\t")
        assertEquals("Jane", row1[0])
        assertEquals("96130", row1[1])

        val row2 = lines[2].split("\t")
        assertEquals("Jane", row2[0])
        assertEquals("96131", row2[1])
    }

    // -------------------------------------------------------------------
    // Test 17: Services column flattens CPT codes when not expanded
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

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )
        val lines = readLines(file)

        // Header + 1 data row
        assertEquals(2, lines.size)

        val data = lines[1].split("\t")
        assertEquals("Jane", data[0])
        // Commas are preserved in TSV (no quoting needed)
        assertEquals("96130, 96131", data[1])
    }

    // -------------------------------------------------------------------
    // Test 18: File uses CRLF line endings
    // -------------------------------------------------------------------

    @Test
    fun `file uses CRLF line endings`() {
        val referral = sampleReferral()
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
            ),
        )

        val file = TsvWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )

        val rawContent = file.readBytes().toString(Charsets.UTF_8)
        assertTrue(rawContent.contains("\r\n"), "TSV should use CRLF line endings")
    }
}
