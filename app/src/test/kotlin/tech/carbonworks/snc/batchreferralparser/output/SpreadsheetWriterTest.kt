package tech.carbonworks.snc.batchreferralparser.output

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.ServiceLine
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [SpreadsheetWriter] covering XLSX output generation.
 *
 * Uses JUnit 5 [TempDir] for isolated file system access. Each test writes
 * a spreadsheet and reads it back with Apache POI to verify content.
 */
class SpreadsheetWriterTest {

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Open the first .xlsx file in [tempDir] as an [XSSFWorkbook]. */
    private fun openWorkbook(file: File): XSSFWorkbook {
        return FileInputStream(file).use { XSSFWorkbook(it) }
    }

    /** Read a cell as a string; returns empty string for blank/missing cells. */
    private fun cellText(workbook: XSSFWorkbook, row: Int, col: Int): String {
        val sheet = workbook.getSheetAt(0)
        val r = sheet.getRow(row) ?: return ""
        val c = r.getCell(col) ?: return ""
        return c.stringCellValue
    }

    /** Read a numeric date cell and convert to [LocalDate]. Returns null if not a date cell. */
    private fun cellDate(workbook: XSSFWorkbook, row: Int, col: Int): LocalDate? {
        val sheet = workbook.getSheetAt(0)
        val r = sheet.getRow(row) ?: return null
        val c = r.getCell(col) ?: return null
        if (c.cellType != CellType.NUMERIC) return null
        val javaDate = c.dateCellValue ?: return null
        return javaDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /** Check if a cell is a numeric (date) cell type. */
    private fun isCellNumeric(workbook: XSSFWorkbook, row: Int, col: Int): Boolean {
        val sheet = workbook.getSheetAt(0)
        val r = sheet.getRow(row) ?: return false
        val c = r.getCell(col) ?: return false
        return c.cellType == CellType.NUMERIC
    }

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
    // Test 1: Single referral — correct column headings and one data row
    // -------------------------------------------------------------------

    @Test
    fun `single referral produces correct column headings and one data row`() {
        val referral = sampleReferral()
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Header row (row 0)
            val headerRow = sheet.getRow(0)
            SpreadsheetWriter.COLUMN_HEADINGS.forEachIndexed { col, expectedHeading ->
                assertEquals(expectedHeading, headerRow.getCell(col).stringCellValue)
            }

            // Exactly one data row (row 1) — last row index should be 1
            assertEquals(1, sheet.lastRowNum)

            // Spot-check data values
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("M", cellText(wb, 1, 1))
            assertEquals("Doe", cellText(wb, 1, 2))
            assertEquals("CASE-001", cellText(wb, 1, 3))
            assertEquals("AUTH-12345", cellText(wb, 1, 4))
            // Date of Issue is now a numeric date cell
            assertEquals(LocalDate.of(2026, 2, 1), cellDate(wb, 1, 6))
            assertEquals("Anytown", cellText(wb, 1, 12))     // City
            assertEquals("CA", cellText(wb, 1, 13))           // State
        }
    }

    // -------------------------------------------------------------------
    // Test 2: Multiple referrals produce multiple rows
    // -------------------------------------------------------------------

    @Test
    fun `multiple referrals produce multiple rows`() {
        val referrals = listOf(
            sampleReferral(firstName = "Alice"),
            sampleReferral(firstName = "Bob"),
            sampleReferral(firstName = "Carol"),
        )
        val file = SpreadsheetWriter.write(referrals, tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Header + 3 data rows
            assertEquals(3, sheet.lastRowNum)
            assertEquals("Alice", cellText(wb, 1, 0))
            assertEquals("Bob", cellText(wb, 2, 0))
            assertEquals("Carol", cellText(wb, 3, 0))
        }
    }

    // -------------------------------------------------------------------
    // Test 3: Empty list produces headers-only spreadsheet
    // -------------------------------------------------------------------

    @Test
    fun `empty list produces headers-only spreadsheet`() {
        val file = SpreadsheetWriter.write(emptyList(), tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Header row present
            val headerRow = sheet.getRow(0)
            assertEquals("First Name", headerRow.getCell(0).stringCellValue)
            assertEquals(
                SpreadsheetWriter.COLUMN_HEADINGS.size,
                headerRow.lastCellNum.toInt(),
            )

            // No data rows — lastRowNum is 0 (only the header)
            assertEquals(0, sheet.lastRowNum)
        }
    }

    // -------------------------------------------------------------------
    // Test 4: No low confidence flag column exists
    // -------------------------------------------------------------------

    @Test
    fun `no low confidence flag column exists`() {
        val referral = sampleReferral()
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)

            // Verify column count is 23 (no Low Confidence Flag)
            assertEquals(23, headerRow.lastCellNum.toInt())

            // Verify "Low Confidence Flag" is not in headings
            for (col in 0 until headerRow.lastCellNum) {
                val heading = headerRow.getCell(col)?.stringCellValue ?: ""
                assertTrue(heading != "Low Confidence Flag",
                    "Low Confidence Flag column should not exist")
            }
        }
    }

    // -------------------------------------------------------------------
    // Test 5: Filename matches authorizations-*.xlsx pattern
    // -------------------------------------------------------------------

    @Test
    fun `filename matches authorizations pattern`() {
        val file = SpreadsheetWriter.write(emptyList(), tempDir, fixedTimestamp)

        assertEquals("authorizations-2026-02-23-143045.xlsx", file.name)
        assertTrue(file.name.matches(Regex("authorizations-\\d{4}-\\d{2}-\\d{2}-\\d{6}\\.xlsx")))
        assertTrue(file.exists())
    }

    // -------------------------------------------------------------------
    // Test 6: Services column flattens CPT codes
    // -------------------------------------------------------------------

    @Test
    fun `services column flattens CPT codes`() {
        val referral = sampleReferral()
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val servicesCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("Services")

        openWorkbook(file).use { wb ->
            assertEquals("96130, 96131", cellText(wb, 1, servicesCol))
        }
    }

    // -------------------------------------------------------------------
    // Test 7 (bonus): Header row is bold and frozen
    // -------------------------------------------------------------------

    @Test
    fun `header row is bold and frozen`() {
        val file = SpreadsheetWriter.write(listOf(sampleReferral()), tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Check bold font on header cell
            val headerCell = sheet.getRow(0).getCell(0)
            val font = wb.getFontAt(headerCell.cellStyle.fontIndex)
            assertTrue(font.bold, "Header font should be bold")

            // Check freeze pane at row 1
            val paneInfo = sheet.paneInformation
            assertTrue(paneInfo != null, "Freeze pane should be set")
            assertEquals(1, paneInfo.horizontalSplitPosition.toInt())
        }
    }

    // -------------------------------------------------------------------
    // Test 8 (bonus): Services with empty list produces empty cell
    // -------------------------------------------------------------------

    @Test
    fun `empty services list produces blank services cell`() {
        val referral = ReferralFields(
            firstName = "Test",
            services = emptyList(),
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val servicesCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("Services")

        openWorkbook(file).use { wb ->
            // Empty services = empty string = no cell created
            assertEquals("", cellText(wb, 1, servicesCol))
        }
    }

    // -------------------------------------------------------------------
    // Test 9: Date of Issue written as Excel date cell
    // -------------------------------------------------------------------

    @Test
    fun `date of issue written as Excel date cell`() {
        val referral = ReferralFields(
            firstName = ("Test"),
            dateOfIssue = ("August 13, 2024"),
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val dateOfIssueCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("Date of Issue")

        openWorkbook(file).use { wb ->
            // Cell should be numeric (date) type
            assertTrue(isCellNumeric(wb, 1, dateOfIssueCol), "Date of Issue cell should be numeric (date)")

            // Date value should match
            val date = cellDate(wb, 1, dateOfIssueCol)
            assertNotNull(date, "Date of Issue should parse to a date")
            assertEquals(LocalDate.of(2024, 8, 13), date)
        }
    }

    // -------------------------------------------------------------------
    // Test 10: DOB written as Excel date cell
    // -------------------------------------------------------------------

    @Test
    fun `dob written as Excel date cell`() {
        val referral = ReferralFields(
            firstName = ("Test"),
            dob = ("09/15/1990"),
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val dobCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("DOB")

        openWorkbook(file).use { wb ->
            // Cell should be numeric (date) type
            assertTrue(isCellNumeric(wb, 1, dobCol), "DOB cell should be numeric (date)")

            // Date value should match
            val date = cellDate(wb, 1, dobCol)
            assertNotNull(date, "DOB should parse to a date")
            assertEquals(LocalDate.of(1990, 9, 15), date)
        }
    }

    // -------------------------------------------------------------------
    // Test 11: Unparseable date falls back to text
    // -------------------------------------------------------------------

    @Test
    fun `unparseable date falls back to text`() {
        val referral = ReferralFields(
            firstName = ("Test"),
            dateOfIssue = ("some unparseable text"),
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val dateOfIssueCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("Date of Issue")

        openWorkbook(file).use { wb ->
            // Cell should be text (string) type, not numeric
            val sheet = wb.getSheetAt(0)
            val cell = sheet.getRow(1).getCell(dateOfIssueCol)
            assertNotNull(cell, "Cell should exist")
            assertEquals(CellType.STRING, cell.cellType, "Unparseable date should be written as text")
            assertEquals("some unparseable text", cell.stringCellValue)
        }
    }

    // -------------------------------------------------------------------
    // Test 12: Appointment date with weekday prefix parses to date cell
    // -------------------------------------------------------------------

    @Test
    fun `appointment date with weekday prefix parses to date cell`() {
        val referral = ReferralFields(
            firstName = ("Test"),
            appointmentDate = ("Thursday September 5th, 2024"),
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        val apptDateCol = SpreadsheetWriter.COLUMN_HEADINGS.indexOf("Appointment Date")

        openWorkbook(file).use { wb ->
            // Cell should be numeric (date) type
            assertTrue(isCellNumeric(wb, 1, apptDateCol), "Appointment Date cell should be numeric (date)")

            // Date value should match — September 5, 2024
            val date = cellDate(wb, 1, apptDateCol)
            assertNotNull(date, "Appointment Date should parse to a date")
            assertEquals(LocalDate.of(2024, 9, 5), date)
        }
    }

    // -------------------------------------------------------------------
    // Test 13: Custom column order produces correctly reordered output
    // -------------------------------------------------------------------

    @Test
    fun `custom column order produces correctly reordered output`() {
        val referral = sampleReferral()
        // Reverse the first three columns: Last Name, Middle Name, First Name
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
                ExportColumn.Field(fieldId = "middleName", displayName = "Middle Name"),
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "caseId", displayName = "Case ID"),
            ),
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)

            // Verify reordered headers
            assertEquals("Last Name", headerRow.getCell(0).stringCellValue)
            assertEquals("Middle Name", headerRow.getCell(1).stringCellValue)
            assertEquals("First Name", headerRow.getCell(2).stringCellValue)
            assertEquals("Case ID", headerRow.getCell(3).stringCellValue)

            // Verify reordered data
            assertEquals("Doe", cellText(wb, 1, 0))
            assertEquals("M", cellText(wb, 1, 1))
            assertEquals("Jane", cellText(wb, 1, 2))
            assertEquals("CASE-001", cellText(wb, 1, 3))

            // Only 4 columns
            assertEquals(4, headerRow.lastCellNum.toInt())
        }
    }

    // -------------------------------------------------------------------
    // Test 14: Disabled fields are excluded from output
    // -------------------------------------------------------------------

    @Test
    fun `disabled fields are excluded from output`() {
        val referral = sampleReferral()
        // Include firstName and lastName, but disable middleName
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "middleName", displayName = "Middle Name", enabled = false),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
                ExportColumn.Field(fieldId = "caseId", displayName = "Case ID"),
            ),
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)

            // Only 3 columns (middleName excluded)
            assertEquals(3, headerRow.lastCellNum.toInt())
            assertEquals("First Name", headerRow.getCell(0).stringCellValue)
            assertEquals("Last Name", headerRow.getCell(1).stringCellValue)
            assertEquals("Case ID", headerRow.getCell(2).stringCellValue)

            // Data row should skip middleName
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("Doe", cellText(wb, 1, 1))
            assertEquals("CASE-001", cellText(wb, 1, 2))
        }
    }

    // -------------------------------------------------------------------
    // Test 15: Spacer columns produce empty cells
    // -------------------------------------------------------------------

    @Test
    fun `spacer columns produce empty cells`() {
        val referral = sampleReferral()
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Spacer(id = "spacer_1"),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
            ),
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)

            // 3 columns: firstName, spacer, lastName
            assertEquals(3, headerRow.lastCellNum.toInt())
            assertEquals("First Name", headerRow.getCell(0).stringCellValue)
            assertEquals("", headerRow.getCell(1).stringCellValue)  // Spacer header is blank
            assertEquals("Last Name", headerRow.getCell(2).stringCellValue)

            // Data: firstName, empty (spacer), lastName
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("", cellText(wb, 1, 1))   // Spacer cell is empty
            assertEquals("Doe", cellText(wb, 1, 2))
        }
    }

    // -------------------------------------------------------------------
    // Test 16: Default config produces identical output to legacy behavior
    // -------------------------------------------------------------------

    @Test
    fun `default config produces identical output to legacy hardcoded behavior`() {
        val referral = sampleReferral()

        // Write with explicit default config
        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp,
            columnConfig = ExportColumnConfig.default(),
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)
            val headerRow = sheet.getRow(0)

            // Verify all 22 column headings match legacy COLUMN_HEADINGS
            assertEquals(SpreadsheetWriter.COLUMN_HEADINGS.size, headerRow.lastCellNum.toInt())
            SpreadsheetWriter.COLUMN_HEADINGS.forEachIndexed { col, expectedHeading ->
                assertEquals(expectedHeading, headerRow.getCell(col).stringCellValue)
            }

            // Verify data values at the same positions as the legacy format
            assertEquals("Jane", cellText(wb, 1, 0))     // First Name
            assertEquals("M", cellText(wb, 1, 1))         // Middle Name
            assertEquals("Doe", cellText(wb, 1, 2))       // Last Name
            assertEquals("CASE-001", cellText(wb, 1, 3))  // Case ID

            // Date columns should still be date cells
            assertEquals(LocalDate.of(2026, 2, 1), cellDate(wb, 1, 6))   // Date of Issue
            assertEquals(LocalDate.of(1990, 5, 15), cellDate(wb, 1, 7))  // DOB
            assertEquals(LocalDate.of(2026, 3, 1), cellDate(wb, 1, 9))   // Appointment Date
        }
    }

    // -------------------------------------------------------------------
    // Test 17: expandServices produces one row per service with duplicated fields
    // -------------------------------------------------------------------

    @Test
    fun `expandServices produces one row per service with duplicated fields`() {
        val referral = sampleReferral() // has services: 96130, 96131

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "lastName", displayName = "Last Name"),
                ExportColumn.Field(fieldId = "caseId", displayName = "Case ID"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = true,
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Two data rows (one per service), not one
            assertEquals(2, sheet.lastRowNum)

            // Row 1: first service
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("Doe", cellText(wb, 1, 1))
            assertEquals("CASE-001", cellText(wb, 1, 2))
            assertEquals("96130", cellText(wb, 1, 3))

            // Row 2: second service, other fields duplicated
            assertEquals("Jane", cellText(wb, 2, 0))
            assertEquals("Doe", cellText(wb, 2, 1))
            assertEquals("CASE-001", cellText(wb, 2, 2))
            assertEquals("96131", cellText(wb, 2, 3))
        }
    }

    // -------------------------------------------------------------------
    // Test 18: expandServices with single service produces one row
    // -------------------------------------------------------------------

    @Test
    fun `expandServices with single service produces one row`() {
        val referral = ReferralFields(
            firstName = "Test",
            services = listOf(ServiceLine(cptCode = "96130")),
        )

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = true,
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Single service = one data row (no expansion needed)
            assertEquals(1, sheet.lastRowNum)
            assertEquals("Test", cellText(wb, 1, 0))
            assertEquals("96130", cellText(wb, 1, 1))
        }
    }

    // -------------------------------------------------------------------
    // Test 19: expandServices false preserves comma-separated behavior
    // -------------------------------------------------------------------

    @Test
    fun `expandServices false preserves comma-separated behavior`() {
        val referral = sampleReferral() // has services: 96130, 96131

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = false,
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = config,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // One data row with comma-separated services
            assertEquals(1, sheet.lastRowNum)
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("96130, 96131", cellText(wb, 1, 1))
        }
    }

    // -------------------------------------------------------------------
    // Test 20: expandServices with multiple referrals expands each independently
    // -------------------------------------------------------------------

    @Test
    fun `expandServices with multiple referrals expands each independently`() {
        val referral1 = ReferralFields(
            firstName = "Alice",
            services = listOf(
                ServiceLine(cptCode = "96130"),
                ServiceLine(cptCode = "96131"),
            ),
        )
        val referral2 = ReferralFields(
            firstName = "Bob",
            services = listOf(ServiceLine(cptCode = "99213")),
        )

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
                ExportColumn.Field(fieldId = "services", displayName = "Services"),
            ),
            expandServices = true,
        )

        val file = SpreadsheetWriter.write(
            listOf(referral1, referral2), tempDir, fixedTimestamp, columnConfig = config,
        )

        openWorkbook(file).use { wb ->
            val sheet = wb.getSheetAt(0)

            // Alice: 2 rows, Bob: 1 row => 3 data rows total
            assertEquals(3, sheet.lastRowNum)

            // Alice row 1
            assertEquals("Alice", cellText(wb, 1, 0))
            assertEquals("96130", cellText(wb, 1, 1))

            // Alice row 2
            assertEquals("Alice", cellText(wb, 2, 0))
            assertEquals("96131", cellText(wb, 2, 1))

            // Bob row
            assertEquals("Bob", cellText(wb, 3, 0))
            assertEquals("99213", cellText(wb, 3, 1))
        }
    }

    // -------------------------------------------------------------------
    // Test 21: Date detection works with custom column order
    // -------------------------------------------------------------------

    @Test
    fun `date detection works with custom column order`() {
        val referral = ReferralFields(
            firstName = "Test",
            dateOfIssue = "August 13, 2024",
            dob = "09/15/1990",
        )
        // Put dateOfIssue at col 0 and dob at col 1 (not their default positions)
        val customConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field(fieldId = "dateOfIssue", displayName = "Date of Issue"),
                ExportColumn.Field(fieldId = "dob", displayName = "DOB"),
                ExportColumn.Field(fieldId = "firstName", displayName = "First Name"),
            ),
        )

        val file = SpreadsheetWriter.write(
            listOf(referral), tempDir, fixedTimestamp, columnConfig = customConfig,
        )

        openWorkbook(file).use { wb ->
            // Date of Issue at col 0 should be a date cell
            assertTrue(isCellNumeric(wb, 1, 0), "dateOfIssue at col 0 should be numeric (date)")
            assertEquals(LocalDate.of(2024, 8, 13), cellDate(wb, 1, 0))

            // DOB at col 1 should be a date cell
            assertTrue(isCellNumeric(wb, 1, 1), "dob at col 1 should be numeric (date)")
            assertEquals(LocalDate.of(1990, 9, 15), cellDate(wb, 1, 1))

            // firstName at col 2 should be text
            assertEquals("Test", cellText(wb, 1, 2))
        }
    }
}
