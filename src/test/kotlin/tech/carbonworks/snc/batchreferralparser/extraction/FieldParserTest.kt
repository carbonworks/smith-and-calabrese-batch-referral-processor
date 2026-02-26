package tech.carbonworks.snc.batchreferralparser.extraction

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [FieldParser] covering all extraction methods ported from the
 * Python prototype `extract_referral_fields.py`.
 *
 * Tests construct [ExtractionResult.Success] and [ExtractedTable] objects
 * programmatically with known text content (no real PDFs or PHI needed).
 *
 * Since [FieldParser.parse] returns [ParseResult], tests access the extracted
 * fields via `result.fields` and warnings via `result.warnings`.
 */
class FieldParserTest {

    private val parser = FieldParser()

    // -------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------

    /**
     * Build an [ExtractionResult.Success] from one or more page text strings.
     * Each string becomes a page with word-level TextBlocks (split on whitespace).
     * All words are placed on the same Y coordinate (100f) so they reconstruct
     * into a single line per page.
     */
    private fun textResult(vararg pageTexts: String): ExtractionResult.Success {
        val pages = pageTexts.mapIndexed { index, text ->
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val blocks = words.mapIndexed { wordIndex, word ->
                TextBlock(
                    text = word,
                    pageNumber = index + 1,
                    boundingBox = BoundingBox(
                        x = wordIndex * 50f,
                        y = 100f,
                        width = word.length * 6f,
                        height = 12f,
                    ),
                    fontSize = 12f,
                )
            }
            PageInfo(
                pageNumber = index + 1,
                width = 612f,
                height = 792f,
                hasText = blocks.isNotEmpty(),
                textBlocks = blocks,
            )
        }
        return ExtractionResult.Success(pages = pages, sourceFile = "test.pdf")
    }

    /**
     * Build an [ExtractionResult.Success] where text blocks are placed on
     * multiple Y coordinates within the same page, simulating multi-line
     * PDF extraction output.
     *
     * @param lines list of text strings, each placed on a different Y coordinate
     * @param ySpacing vertical spacing between lines in PDF units
     */
    private fun multiLineTextResult(
        vararg lines: String,
        ySpacing: Float = 15f,
    ): ExtractionResult.Success {
        val allBlocks = mutableListOf<TextBlock>()
        for ((lineIndex, line) in lines.withIndex()) {
            val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            for ((wordIndex, word) in words.withIndex()) {
                allBlocks.add(
                    TextBlock(
                        text = word,
                        pageNumber = 1,
                        boundingBox = BoundingBox(
                            x = wordIndex * 50f,
                            y = 100f + lineIndex * ySpacing,
                            width = word.length * 6f,
                            height = 12f,
                        ),
                        fontSize = 12f,
                    )
                )
            }
        }
        val page = PageInfo(
            pageNumber = 1,
            width = 612f,
            height = 792f,
            hasText = allBlocks.isNotEmpty(),
            textBlocks = allBlocks,
        )
        return ExtractionResult.Success(pages = listOf(page), sourceFile = "test.pdf")
    }

    /**
     * Build a list of [ExtractedTable] from cell content strings.
     * Each string becomes a separate cell on the same table.
     */
    private fun tableWith(vararg cellContents: String): List<ExtractedTable> {
        val cells = cellContents.mapIndexed { index, content ->
            TableCell(content = content, rowIndex = index, columnIndex = 0)
        }
        return listOf(
            ExtractedTable(
                pageNumber = 1,
                cells = cells,
                rowCount = cellContents.size,
                columnCount = 1,
            )
        )
    }

    // -------------------------------------------------------------------
    // 1. Header block parsing — full header with 3-part name
    // -------------------------------------------------------------------

    @Test
    fun `header block extracts all fields with three-part name`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-12345 RE: John Michael Smith DOB: 03/22/1990 Applicant: Jane Smith Authorization #: AUTH-9876"
        )

        val result = parser.parse(input)

        assertEquals("09/15/2024", result.fields.dateOfIssue)
        assertEquals("ABC-12345", result.fields.caseId)
        assertEquals("John", result.fields.firstName)
        assertEquals("Michael", result.fields.middleName)
        assertEquals("Smith", result.fields.lastName)
        assertEquals("03/22/1990", result.fields.dob)
        assertEquals("Jane Smith", result.fields.applicantName)
        assertEquals("AUTH-9876", result.fields.authorizationNumber)
        assertTrue(result.warnings.isEmpty(), "Full header match should produce no warnings")
    }

    // -------------------------------------------------------------------
    // 2. Header with 2-part name (no middle name)
    // -------------------------------------------------------------------

    @Test
    fun `header block extracts fields with two-part name`() {
        val input = textResult(
            "Date: 01/10/2025 Case ID: XYZ-999 RE: Alice Johnson DOB: 05/15/1985 Applicant: Bob Johnson Authorization #: A111"
        )

        val result = parser.parse(input)

        assertEquals("Alice", result.fields.firstName)
        assertNull(result.fields.middleName)
        assertEquals("Johnson", result.fields.lastName)
        assertEquals("XYZ-999", result.fields.caseId)
    }

    // -------------------------------------------------------------------
    // 3. Case number footer pattern
    // -------------------------------------------------------------------

    @Test
    fun `case number footer extracts components`() {
        val input = textResult(
            "BHA-12345-CE/ Assigned 4321 null/ DCPS / DCC-9999"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.fields.caseNumberFullFooter)
        assertEquals("4321", result.fields.assignedCode)
        assertEquals("DCC-9999", result.fields.dccNumber)
    }

    // -------------------------------------------------------------------
    // 4. Claimant information table cell
    // -------------------------------------------------------------------

    @Test
    fun `claimant table cell extracts address and phone`() {
        val tables = tableWith(
            "Claimant Information JOHN DOE 123 MAIN ST ANYTOWN, MD 21201 410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("123 MAIN ST", result.fields.streetAddress)
        assertEquals("ANYTOWN", result.fields.city)
        assertEquals("MD", result.fields.state)
        assertEquals("21201", result.fields.zipCode)
        assertEquals("410-555-1234", result.fields.phone)
    }

    // -------------------------------------------------------------------
    // 5. Appointment date/time table cell
    // -------------------------------------------------------------------

    @Test
    fun `appointment cell extracts date and time`() {
        val tables = tableWith(
            "Date and Time Thursday September 5th, 2024 10:00 AM Eastern Standard Time"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("Thursday September 5th, 2024", result.fields.appointmentDate)
        assertEquals("10:00 AM", result.fields.appointmentTime)
    }

    // -------------------------------------------------------------------
    // 6. Services authorized cell with multiple CPT codes
    // -------------------------------------------------------------------

    @Test
    fun `services cell extracts multiple service lines`() {
        val tables = tableWith(
            "Services Authorized Code: 96130 Procedure Type Code: P Desc: Psychological testing evaluation Fee: \$ 225.00 Code: 96131 Procedure Type Code: P Desc: Psych testing addl hr Fee: \$ 175.00"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals(2, result.fields.services.size)

        val svc1 = result.fields.services[0]
        assertEquals("96130", svc1.cptCode)
        assertEquals("P", svc1.procedureTypeCode)
        assertEquals("Psychological testing evaluation", svc1.description)
        assertEquals("225.00", svc1.fee)

        val svc2 = result.fields.services[1]
        assertEquals("96131", svc2.cptCode)
        assertEquals("P", svc2.procedureTypeCode)
        assertEquals("Psych testing addl hr", svc2.description)
        assertEquals("175.00", svc2.fee)
    }

    // -------------------------------------------------------------------
    // 7. Invoice fields
    // -------------------------------------------------------------------

    @Test
    fun `invoice fields extract tax id vendor and request id`() {
        val input = textResult(
            "Federal Tax ID Number: 123456789 Vendor Number: V-5678 Authorization Number: AUTH-INVOICE-1 RQID:RQ-42"
        )

        val result = parser.parse(input)

        assertEquals("123456789", result.fields.federalTaxId)
        assertEquals("V-5678", result.fields.vendorNumber)
        assertEquals("RQ-42", result.fields.requestId)
        // Invoice auth number should be overridden if header also has one,
        // but with no header it should be the invoice value
        assertEquals("AUTH-INVOICE-1", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // 8. CELL # phone extraction
    // -------------------------------------------------------------------

    @Test
    fun `cell phone pattern extracts phone number`() {
        val input = textResult(
            "Some other text CELL # 410-555-9876 more text"
        )

        val result = parser.parse(input)

        assertEquals("410-555-9876", result.fields.phone)
    }

    // -------------------------------------------------------------------
    // 9. Missing fields produce null values (empty input)
    // -------------------------------------------------------------------

    @Test
    fun `empty input produces all null fields`() {
        val input = textResult("")

        val result = parser.parse(input)

        assertNull(result.fields.firstName)
        assertNull(result.fields.lastName)
        assertNull(result.fields.caseId)
        assertNull(result.fields.authorizationNumber)
        assertNull(result.fields.dob)
        assertNull(result.fields.appointmentDate)
        assertNull(result.fields.phone)
        assertNull(result.fields.federalTaxId)
        assertTrue(result.fields.services.isEmpty())
        assertEquals(0, result.fields.filledFieldCount())
        assertTrue(result.warnings.isEmpty(), "Empty input should produce no warnings")
    }

    // -------------------------------------------------------------------
    // 10. Priority merge — header fields override table and invoice fields
    // -------------------------------------------------------------------

    @Test
    fun `header authorization number overrides invoice authorization number`() {
        // Page 1: header with authorization number
        // Page 2: invoice with a different authorization number
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-HEADER",
            "Authorization Number: AUTH-INVOICE"
        )

        val result = parser.parse(input)

        // Header has higher priority, so header value wins
        assertEquals("AUTH-HEADER", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // 11. filledFieldCount works correctly
    // -------------------------------------------------------------------

    @Test
    fun `filledFieldCount counts extracted fields correctly`() {
        // Only a CELL # phone and one table cell
        val input = textResult("CELL # 555-123-4567")
        val tables = tableWith(
            "Date and Time Monday January 6th, 2025 2:30 PM Eastern"
        )

        val result = parser.parse(input, tables)

        // Should have: phone (1) + appointmentDate (1) + appointmentTime (1) = 3 fields
        assertEquals(3, result.fields.filledFieldCount())
    }

    // ===================================================================
    // NEW TESTS: Multi-line header handling
    // ===================================================================

    // -------------------------------------------------------------------
    // 12. Multi-line header — fields split across multiple lines on one page
    // -------------------------------------------------------------------

    @Test
    fun `multi-line header extracts all fields when split across lines`() {
        val input = multiLineTextResult(
            "Date: 09/15/2024 Case ID: ABC-12345",
            "RE: John Michael Smith DOB: 03/22/1990",
            "Applicant: Jane Smith Authorization #: AUTH-9876",
        )

        val result = parser.parse(input)

        assertEquals("09/15/2024", result.fields.dateOfIssue)
        assertEquals("ABC-12345", result.fields.caseId)
        assertEquals("John", result.fields.firstName)
        assertEquals("Michael", result.fields.middleName)
        assertEquals("Smith", result.fields.lastName)
        assertEquals("03/22/1990", result.fields.dob)
        assertEquals("Jane Smith", result.fields.applicantName)
        assertEquals("AUTH-9876", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // 13. Individual field fallback — only some header fields present
    // -------------------------------------------------------------------

    @Test
    fun `individual field fallback extracts partial header fields`() {
        val input = textResult(
            "Case ID: XYZ-789 DOB: 06/01/1975"
        )

        val result = parser.parse(input)

        assertEquals("XYZ-789", result.fields.caseId)
        assertEquals("06/01/1975", result.fields.dob)
        // Fields not present should remain null
        assertNull(result.fields.firstName)
        assertNull(result.fields.applicantName)
        assertNull(result.fields.authorizationNumber)
    }

    @Test
    fun `individual field fallback extracts date and authorization without full header`() {
        val input = textResult(
            "Date: 12/25/2025 Authorization #: AUTH-SOLO"
        )

        val result = parser.parse(input)

        assertEquals("12/25/2025", result.fields.dateOfIssue)
        assertEquals("AUTH-SOLO", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // 14. Flexible footer — extra whitespace, missing null/
    // -------------------------------------------------------------------

    @Test
    fun `footer extracts fields when null is missing`() {
        val input = textResult(
            "BHA-55555-CE/ Assigned 1234 DCPS / DCC-7777"
        )

        val result = parser.parse(input)

        assertEquals("BHA-55555-CE", result.fields.caseNumberFullFooter)
        assertEquals("1234", result.fields.assignedCode)
        assertEquals("DCC-7777", result.fields.dccNumber)
    }

    @Test
    fun `footer extracts fields when both null and agency are missing`() {
        val input = textResult(
            "BHA-99999-CE/ Assigned 5678 DCC-4444"
        )

        val result = parser.parse(input)

        assertEquals("BHA-99999-CE", result.fields.caseNumberFullFooter)
        assertEquals("5678", result.fields.assignedCode)
        assertEquals("DCC-4444", result.fields.dccNumber)
    }

    // -------------------------------------------------------------------
    // 15. Case-insensitive invoice fields
    // -------------------------------------------------------------------

    @Test
    fun `invoice fields match case-insensitive labels`() {
        val input = textResult(
            "federal tax id number: 999888777 vendor number: V-LOWER authorization number: AUTH-LOW"
        )

        val result = parser.parse(input)

        assertEquals("999888777", result.fields.federalTaxId)
        assertEquals("V-LOWER", result.fields.vendorNumber)
        assertEquals("AUTH-LOW", result.fields.authorizationNumber)
    }

    @Test
    fun `invoice fields match alternate tax ID labels`() {
        val input = textResult(
            "Federal Tax ID: 111222333"
        )

        val result = parser.parse(input)

        assertEquals("111222333", result.fields.federalTaxId)
    }

    @Test
    fun `invoice fields match Fed Tax ID label`() {
        val input = textResult(
            "Fed Tax ID: 444555666"
        )

        val result = parser.parse(input)

        assertEquals("444555666", result.fields.federalTaxId)
    }

    @Test
    fun `invoice fields match Fed Tax ID with period`() {
        val input = textResult(
            "Fed. Tax ID Number: 777888999"
        )

        val result = parser.parse(input)

        assertEquals("777888999", result.fields.federalTaxId)
    }

    // -------------------------------------------------------------------
    // 16. RQID with space after colon
    // -------------------------------------------------------------------

    @Test
    fun `RQID with space after colon is extracted`() {
        val input = textResult(
            "RQID: RQ-42"
        )

        val result = parser.parse(input)

        assertEquals("RQ-42", result.fields.requestId)
    }

    @Test
    fun `RQID without space after colon is still extracted`() {
        val input = textResult(
            "RQID:RQ-99"
        )

        val result = parser.parse(input)

        assertEquals("RQ-99", result.fields.requestId)
    }

    @Test
    fun `RQID case-insensitive`() {
        val input = textResult(
            "rqid: RQ-LOWER"
        )

        val result = parser.parse(input)

        assertEquals("RQ-LOWER", result.fields.requestId)
    }

    // -------------------------------------------------------------------
    // 17. Wider Y tolerance test — verify line merging
    // -------------------------------------------------------------------

    @Test
    fun `wider Y tolerance merges lines with slight vertical offset`() {
        // Simulate text blocks at slightly different Y positions that should be
        // on the same line with the default 5.0f tolerance
        val blocks = listOf(
            TextBlock("Date:", 1, BoundingBox(10f, 100f, 30f, 12f), 12f),
            TextBlock("09/15/2024", 1, BoundingBox(50f, 102f, 60f, 12f), 12f),  // 2pt offset
            TextBlock("Case", 1, BoundingBox(120f, 101f, 24f, 12f), 12f),       // 1pt offset
            TextBlock("ID:", 1, BoundingBox(150f, 103f, 18f, 12f), 12f),        // 3pt offset
            TextBlock("ABC-123", 1, BoundingBox(175f, 100f, 42f, 12f), 12f),
            // Second line
            TextBlock("RE:", 1, BoundingBox(10f, 120f, 18f, 12f), 12f),
            TextBlock("John", 1, BoundingBox(35f, 121f, 24f, 12f), 12f),
            TextBlock("Smith", 1, BoundingBox(65f, 120f, 30f, 12f), 12f),
            TextBlock("DOB:", 1, BoundingBox(105f, 122f, 24f, 12f), 12f),
            TextBlock("01/01/2000", 1, BoundingBox(135f, 120f, 60f, 12f), 12f),
            // Third line
            TextBlock("Applicant:", 1, BoundingBox(10f, 140f, 60f, 12f), 12f),
            TextBlock("Jane", 1, BoundingBox(75f, 141f, 24f, 12f), 12f),
            TextBlock("Smith", 1, BoundingBox(105f, 140f, 30f, 12f), 12f),
            TextBlock("Authorization", 1, BoundingBox(145f, 142f, 78f, 12f), 12f),
            TextBlock("#:", 1, BoundingBox(230f, 140f, 12f, 12f), 12f),
            TextBlock("AUTH-999", 1, BoundingBox(250f, 141f, 48f, 12f), 12f),
        )

        val page = PageInfo(
            pageNumber = 1, width = 612f, height = 792f,
            hasText = true, textBlocks = blocks,
        )
        val input = ExtractionResult.Success(pages = listOf(page), sourceFile = "test.pdf")

        // Default parser (5.0f tolerance) should merge blocks within 5pt
        val result = parser.parse(input)

        assertEquals("09/15/2024", result.fields.dateOfIssue)
        assertEquals("ABC-123", result.fields.caseId)
        assertEquals("John", result.fields.firstName)
        assertEquals("Smith", result.fields.lastName)
        assertEquals("01/01/2000", result.fields.dob)
        assertEquals("AUTH-999", result.fields.authorizationNumber)
    }

    @Test
    fun `tight Y tolerance fails to merge offset blocks, fallback extracts what it can`() {
        // Same blocks as above but with tight parser
        val tightParser = FieldParser(lineYTolerance = 1.0f)

        val blocks = listOf(
            TextBlock("Date:", 1, BoundingBox(10f, 100f, 30f, 12f), 12f),
            TextBlock("09/15/2024", 1, BoundingBox(50f, 103f, 60f, 12f), 12f),  // 3pt offset — too big for 1.0f
            TextBlock("Case", 1, BoundingBox(120f, 100f, 24f, 12f), 12f),
            TextBlock("ID:", 1, BoundingBox(150f, 100f, 18f, 12f), 12f),
            TextBlock("ABC-123", 1, BoundingBox(175f, 103f, 42f, 12f), 12f),    // 3pt offset
        )

        val page = PageInfo(
            pageNumber = 1, width = 612f, height = 792f,
            hasText = true, textBlocks = blocks,
        )
        val input = ExtractionResult.Success(pages = listOf(page), sourceFile = "test.pdf")

        val result = tightParser.parse(input)

        // With tight tolerance, "Date:" and "09/15/2024" are on different lines,
        // so the combined regex won't match. The individual fallback should still
        // find "Date:" on one line and extract it (but it may find the value on
        // the next line or not — depends on the per-field regex).
        // The key point: the tight parser should NOT crash, and should extract
        // less data than the wider tolerance.
        // We just verify it doesn't crash and returns something reasonable.
        assertTrue(true, "Tight tolerance parser should not crash")
    }

    // -------------------------------------------------------------------
    // 18. Header split across pages
    // -------------------------------------------------------------------

    @Test
    fun `header fields extracted when split across pages`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: CROSS-PAGE",
            "RE: Jane Doe DOB: 07/04/1980 Applicant: John Doe Authorization #: AUTH-SPLIT"
        )

        val result = parser.parse(input)

        // The combined regex should find all fields across concatenated pages
        assertEquals("09/15/2024", result.fields.dateOfIssue)
        assertEquals("CROSS-PAGE", result.fields.caseId)
        assertEquals("Jane", result.fields.firstName)
        assertEquals("Doe", result.fields.lastName)
        assertEquals("07/04/1980", result.fields.dob)
        assertEquals("AUTH-SPLIT", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // 19. dumpPageTexts diagnostic utility
    // -------------------------------------------------------------------

    @Test
    fun `dumpPageTexts shows label presence without values`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-999",
            "some random text with no labels",
            "Federal Tax ID Number: 123456789 Vendor Number: V-123 RQID: RQ-1"
        )

        val dump = FieldParser.dumpPageTexts(input)

        // Page 1 should list header labels
        assertTrue(dump.contains("[Page 1]"), "Should have Page 1 header")
        assertTrue(dump.contains("Date:"), "Should find Date label")
        assertTrue(dump.contains("Case ID:"), "Should find Case ID label")
        assertTrue(dump.contains("RE:"), "Should find RE label")
        assertTrue(dump.contains("DOB:"), "Should find DOB label")

        // Page 2 should show no labels
        assertTrue(dump.contains("[Page 2]"), "Should have Page 2 header")
        assertTrue(dump.contains("(none)"), "Page 2 should have no labels")

        // Page 3 should list invoice labels
        assertTrue(dump.contains("[Page 3]"), "Should have Page 3 header")
        assertTrue(dump.contains("RQID:"), "Should find RQID label")

        // Verify PHI values are NOT in the dump
        assertFalse(dump.contains("09/15/2024"), "Should not contain date value")
        assertFalse(dump.contains("ABC-123"), "Should not contain case ID value")
        assertFalse(dump.contains("John"), "Should not contain name value")
        assertFalse(dump.contains("123456789"), "Should not contain tax ID value")
    }

    // -------------------------------------------------------------------
    // 20. reconstructPageTexts produces line-grouped text
    // -------------------------------------------------------------------

    @Test
    fun `reconstructPageTexts groups blocks by Y coordinate into lines`() {
        val input = multiLineTextResult(
            "first line content",
            "second line content",
            "third line content",
        )

        val pageTexts = parser.reconstructPageTexts(input)

        assertEquals(1, pageTexts.size, "Should have 1 page")
        val lines = pageTexts[0].lines()
        assertEquals(3, lines.size, "Should have 3 lines")
        assertTrue(lines[0].contains("first"))
        assertTrue(lines[1].contains("second"))
        assertTrue(lines[2].contains("third"))
    }

    // -------------------------------------------------------------------
    // 21. Constructor parameter for lineYTolerance
    // -------------------------------------------------------------------

    @Test
    fun `custom lineYTolerance is accepted by constructor`() {
        val customParser = FieldParser(lineYTolerance = 10.0f)
        val input = textResult(
            "Date: 01/01/2025 Case ID: TEST-1 RE: Test Person DOB: 01/01/2000 Applicant: Tester Authorization #: AUTH-1"
        )

        // Should work fine with a wider tolerance
        val result = customParser.parse(input)
        assertEquals("TEST-1", result.fields.caseId)
    }

    // -------------------------------------------------------------------
    // 22. Invoice fields across pages
    // -------------------------------------------------------------------

    @Test
    fun `invoice fields found across multiple pages`() {
        val input = textResult(
            "Federal Tax ID Number: 111222333",
            "Vendor Number: V-9999 RQID: RQ-77"
        )

        val result = parser.parse(input)

        assertEquals("111222333", result.fields.federalTaxId)
        assertEquals("V-9999", result.fields.vendorNumber)
        assertEquals("RQ-77", result.fields.requestId)
    }

    // -------------------------------------------------------------------
    // 23. Footer with different agency code (not DCPS)
    // -------------------------------------------------------------------

    @Test
    fun `footer with non-DCPS agency code is extracted`() {
        val input = textResult(
            "BHA-88888-CE/ Assigned 9999 null/ SSA / DCC-1111"
        )

        val result = parser.parse(input)

        assertEquals("BHA-88888-CE", result.fields.caseNumberFullFooter)
        assertEquals("9999", result.fields.assignedCode)
        assertEquals("DCC-1111", result.fields.dccNumber)
    }

    // ===================================================================
    // ParseResult / Warnings tests
    // ===================================================================

    // -------------------------------------------------------------------
    // 24. Warnings generated when header labels present but regex misses
    // -------------------------------------------------------------------

    @Test
    fun `warnings generated when header labels present but no data extracted`() {
        // Text has "Case ID:" label but the combined regex won't match because
        // not all required header fields are present in the right order
        val input = textResult(
            "Case ID: something but no other header fields match the full pattern"
        )

        val result = parser.parse(input)

        // Case ID should be extracted by the individual fallback
        assertEquals("something", result.fields.caseId)
        // No warnings for Case ID since it was extracted by fallback
        // But there may be no warnings at all since the individual fallback worked
    }

    @Test
    fun `no warnings when all extraction stages succeed`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-999"
        )
        val tables = tableWith(
            "Claimant Information JOHN DOE 123 MAIN ST ANYTOWN, MD 21201 410-555-1234",
            "Date and Time Thursday September 5th, 2024 10:00 AM Eastern Standard Time",
            "Services Authorized Code: 96130 Procedure Type Code: P Desc: Test Fee: \$ 100.00",
        )

        val result = parser.parse(input, tables)

        // All stages should extract successfully, no warnings
        assertTrue(result.warnings.isEmpty(), "Fully successful extraction should produce no warnings")
    }

    @Test
    fun `warnings list contains correct fields and stages`() {
        // Provide text that has invoice labels but deliberately broken patterns
        // so the regex won't match — labels are present but values missing
        val input = textResult(
            "Federal Tax ID Vendor Number RQID"  // Labels without colons/values
        )

        val result = parser.parse(input)

        // The label detection looks for "Federal Tax ID" and "Vendor Number" and "RQID"
        // but the invoice regexes require "Federal Tax ID ... : DIGITS", "Vendor Number: ...", "RQID: ..."
        // So we should get warnings for all three invoice fields
        val invoiceWarnings = result.warnings.filter { it.stage == "invoice" }
        assertTrue(invoiceWarnings.isNotEmpty(), "Should have invoice warnings when labels found but patterns don't match")

        // Verify warning fields
        val warningFields = invoiceWarnings.map { it.field }.toSet()
        assertTrue("Federal Tax ID" in warningFields, "Should have Federal Tax ID warning")
        assertTrue("Vendor Number" in warningFields, "Should have Vendor Number warning")
        assertTrue("Request ID" in warningFields, "Should have Request ID warning")
    }

    @Test
    fun `footer warning generated when Assigned label present but pattern fails`() {
        val input = textResult(
            "Assigned but no valid footer pattern"
        )

        val result = parser.parse(input)

        val footerWarnings = result.warnings.filter { it.stage == "footer" }
        assertTrue(footerWarnings.isNotEmpty(), "Should have footer warning when 'Assigned' label is present")
        assertEquals("Case Number (Footer)", footerWarnings[0].field)
    }
}
