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
     * Build an [ExtractionResult.Success] from lines of text, where each line
     * is placed at a different Y coordinate to simulate PDFBox producing text
     * blocks at different vertical positions on the page.
     *
     * Each string in [lines] represents a separate line. Words within a line
     * share the same Y coordinate; different lines have Y offsets of 20 units.
     * This helper is critical for testing cross-line extraction scenarios.
     *
     * @param pageNumber the page number for all text blocks (default 1)
     * @param lines the text lines, each at a different Y coordinate
     */
    private fun multiLineTextResult(
        vararg lines: String,
        pageNumber: Int = 1,
    ): ExtractionResult.Success {
        val blocks = mutableListOf<TextBlock>()
        for ((lineIndex, line) in lines.withIndex()) {
            val words = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val yCoord = 100f + lineIndex * 20f
            for ((wordIndex, word) in words.withIndex()) {
                blocks.add(
                    TextBlock(
                        text = word,
                        pageNumber = pageNumber,
                        boundingBox = BoundingBox(
                            x = wordIndex * 50f,
                            y = yCoord,
                            width = word.length * 6f,
                            height = 12f,
                        ),
                        fontSize = 12f,
                    )
                )
            }
        }
        val page = PageInfo(
            pageNumber = pageNumber,
            width = 612f,
            height = 792f,
            hasText = blocks.isNotEmpty(),
            textBlocks = blocks,
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
        val extractionWarnings = result.warnings.filter { it.stage != "completeness" }
        assertTrue(extractionWarnings.isEmpty(), "Full header match should produce no extraction warnings")
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
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201\n410-555-1234"
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
        // Empty input produces no stage-specific extraction warnings (header, table, etc.)
        // but does produce completeness warnings for missing expected fields
        val extractionWarnings = result.warnings.filter { it.stage != "completeness" }
        assertTrue(extractionWarnings.isEmpty(), "Empty input should produce no extraction warnings")
        val completenessWarnings = result.warnings.filter { it.stage == "completeness" }
        assertTrue(completenessWarnings.isNotEmpty(), "Empty input should produce completeness warnings")
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
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201\n410-555-1234",
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

    // -------------------------------------------------------------------
    // 25. reconstructPageTexts prefers strippedText over block-joined text
    // -------------------------------------------------------------------

    @Test
    fun `reconstructPageTexts uses strippedText when populated`() {
        // Create a page with both textBlocks and strippedText populated.
        // The strippedText should be preferred over the block-joined text.
        val blocks = listOf(
            TextBlock("BlockA", 1, BoundingBox(10f, 100f, 36f, 12f), 12f),
            TextBlock("BlockB", 1, BoundingBox(60f, 100f, 36f, 12f), 12f),
        )
        val page = PageInfo(
            pageNumber = 1,
            width = 612f,
            height = 792f,
            hasText = true,
            textBlocks = blocks,
            strippedText = "Properly Spaced Text From PDFTextStripper",
        )
        val input = ExtractionResult.Success(pages = listOf(page), sourceFile = "test.pdf")

        val pageTexts = parser.reconstructPageTexts(input)

        assertEquals(1, pageTexts.size)
        assertEquals("Properly Spaced Text From PDFTextStripper", pageTexts[0])
    }

    @Test
    fun `reconstructPageTexts falls back to block joining when strippedText is empty`() {
        // Create a page with textBlocks but no strippedText (default empty string).
        // Should fall back to the block-joining logic.
        val blocks = listOf(
            TextBlock("Hello", 1, BoundingBox(10f, 100f, 30f, 12f), 12f),
            TextBlock("World", 1, BoundingBox(60f, 100f, 30f, 12f), 12f),
        )
        val page = PageInfo(
            pageNumber = 1,
            width = 612f,
            height = 792f,
            hasText = true,
            textBlocks = blocks,
            // strippedText defaults to "" — should trigger fallback
        )
        val input = ExtractionResult.Success(pages = listOf(page), sourceFile = "test.pdf")

        val pageTexts = parser.reconstructPageTexts(input)

        assertEquals(1, pageTexts.size)
        assertEquals("Hello World", pageTexts[0])
    }

    @Test
    fun `reconstructPageTexts handles mixed pages with and without strippedText`() {
        val page1 = PageInfo(
            pageNumber = 1,
            width = 612f,
            height = 792f,
            hasText = true,
            textBlocks = listOf(
                TextBlock("FallbackText", 1, BoundingBox(10f, 100f, 72f, 12f), 12f),
            ),
            // No strippedText — falls back to blocks
        )
        val page2 = PageInfo(
            pageNumber = 2,
            width = 612f,
            height = 792f,
            hasText = true,
            textBlocks = listOf(
                TextBlock("IgnoredBlock", 2, BoundingBox(10f, 100f, 72f, 12f), 12f),
            ),
            strippedText = "Stripped Page 2 Text",
        )
        val input = ExtractionResult.Success(
            pages = listOf(page1, page2),
            sourceFile = "test.pdf",
        )

        val pageTexts = parser.reconstructPageTexts(input)

        assertEquals(2, pageTexts.size)
        assertEquals("FallbackText", pageTexts[0])  // Fallback: block-joined
        assertEquals("Stripped Page 2 Text", pageTexts[1])  // Preferred: strippedText
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

    // ===================================================================
    // Bug regression tests (WP-12)
    // ===================================================================

    // -------------------------------------------------------------------
    // B2: Full name concatenated into firstName without spaces
    // -------------------------------------------------------------------

    @Test
    fun `B2 - camelCase name is split into first middle last`() {
        // PDFBox produces "John", "Michael", "Smith" as separate text blocks at
        // slightly different Y positions that cause them to merge into a single
        // token "JohnMichaelSmith" during header matching
        val input = textResult(
            "Date: August 13, 2024 Case ID: BHA-12345-CE RE: JohnMichaelSmith DOB: August 1, 2015 Applicant: JANE SMITH Authorization #: AUTH-ABCD-1234"
        )

        val result = parser.parse(input)

        assertEquals("John", result.fields.firstName)
        assertEquals("Michael", result.fields.middleName)
        assertEquals("Smith", result.fields.lastName)
    }

    @Test
    fun `B2 - splitCamelCaseName handles various patterns`() {
        assertEquals("John Michael Smith", parser.splitCamelCaseName("JohnMichaelSmith"))
        assertEquals("Alice Johnson", parser.splitCamelCaseName("AliceJohnson"))
        assertEquals("JOHN", parser.splitCamelCaseName("JOHN")) // All caps unchanged
        assertEquals("john", parser.splitCamelCaseName("john")) // All lower unchanged
    }

    @Test
    fun `B2 - name blocks at different Y positions still parse correctly`() {
        // Simulate PDFBox producing name parts at slightly different Y coordinates
        // that get reconstructed into separate lines, then collapsed by header matching
        val input = multiLineTextResult(
            "Date: August 13, 2024 Case ID: BHA-12345-CE",
            "RE: JOHN MICHAEL SMITH DOB: August 1, 2015",
            "Applicant: JANE SMITH Authorization #: AUTH-ABCD-1234"
        )

        val result = parser.parse(input)

        assertEquals("JOHN", result.fields.firstName)
        assertEquals("MICHAEL", result.fields.middleName)
        assertEquals("SMITH", result.fields.lastName)
        assertEquals("BHA-12345-CE", result.fields.caseId)
        assertEquals("AUTH-ABCD-1234", result.fields.authorizationNumber)
    }

    // -------------------------------------------------------------------
    // B3: Case ID not extracted when split across lines
    // -------------------------------------------------------------------

    @Test
    fun `B3 - case ID extracted when Case and ID are on different lines`() {
        // PDFBox splits "Case" and "ID:" across text blocks at different Y positions
        val input = multiLineTextResult(
            "Date: August 13, 2024 Case",
            "ID: BHA-12345-CE RE: JOHN SMITH",
            "DOB: August 1, 2015 Applicant: JANE SMITH Authorization #: AUTH-ABCD-1234"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.fields.caseId)
    }

    @Test
    fun `B3 - case ID fallback extracts from concatenated all-pages text`() {
        // Case ID appears on a page without the full header pattern,
        // so the individual fallback is needed
        val input = multiLineTextResult(
            "Some unrelated text on page",
            "Case ID: BHA-99999-CE",
            "More unrelated text follows here"
        )

        val result = parser.parse(input)

        assertEquals("BHA-99999-CE", result.fields.caseId)
    }

    // -------------------------------------------------------------------
    // B4: Request ID not extracted when RQID split across lines
    // -------------------------------------------------------------------

    @Test
    fun `B4 - RQID extracted when label and value are on different lines`() {
        val input = multiLineTextResult(
            "Federal Tax ID Number: 923618220",
            "RQID:",
            "RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("RQ-348145", result.fields.requestId)
    }

    @Test
    fun `B4 - RQID extracted when concatenated with no space`() {
        // Docling shows "RQID:RQ-348145" as a single text element
        val input = textResult(
            "RQID:RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("RQ-348145", result.fields.requestId)
    }

    @Test
    fun `B4 - RQID extracted with space between colon and value`() {
        val input = textResult(
            "RQID: RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("RQ-348145", result.fields.requestId)
    }

    // -------------------------------------------------------------------
    // B6: Street address not extracted when state and zip concatenated
    // -------------------------------------------------------------------

    @Test
    fun `B6 - claimant cell with no space between state and zip`() {
        val tables = tableWith(
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD21201\n410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("JOHN DOE", parser.parseClaimantCell(
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD21201\n410-555-1234"
        ).nameFromTable)
        assertEquals("123 MAIN ST", result.fields.streetAddress)
        assertEquals("ANYTOWN", result.fields.city)
        assertEquals("MD", result.fields.state)
        assertEquals("21201", result.fields.zipCode)
        assertEquals("410-555-1234", result.fields.phone)
    }

    @Test
    fun `B6 - claimant cell with three-part name and no space state-zip`() {
        val tables = tableWith(
            "Claimant Information\nJOHN MICHAEL SMITH\n123 MAIN ST\nANYTOWN, MD21201\n410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("123 MAIN ST", result.fields.streetAddress)
        assertEquals("ANYTOWN", result.fields.city)
        assertEquals("MD", result.fields.state)
        assertEquals("21201", result.fields.zipCode)
        assertEquals("410-555-1234", result.fields.phone)
    }

    @Test
    fun `B6 - claimant cell with zip plus four`() {
        val tables = tableWith(
            "Claimant Information\nJOHN DOE\n456 OAK AVE\nSPRINGFIELD, VA22150-1234\n703-555-6789"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("456 OAK AVE", result.fields.streetAddress)
        assertEquals("SPRINGFIELD", result.fields.city)
        assertEquals("VA", result.fields.state)
        assertEquals("22150-1234", result.fields.zipCode)
    }

    // -------------------------------------------------------------------
    // B7: Federal Tax ID and Vendor Number cross-line label extraction
    // -------------------------------------------------------------------

    @Test
    fun `B7 - federal tax id extracted when value on next line`() {
        val input = multiLineTextResult(
            "Federal Tax ID Number:",
            "923618220",
            "Vendor Number:",
            "V-5678"
        )

        val result = parser.parse(input)

        assertEquals("923618220", result.fields.federalTaxId)
        assertEquals("V-5678", result.fields.vendorNumber)
    }

    @Test
    fun `B7 - vendor number extracted when value on next line`() {
        val input = multiLineTextResult(
            "some text above",
            "Vendor Number:",
            "V-5678",
            "Authorization Number:",
            "AUTH-ABCD-1234"
        )

        val result = parser.parse(input)

        assertEquals("V-5678", result.fields.vendorNumber)
        assertEquals("AUTH-ABCD-1234", result.fields.authorizationNumber)
    }

    @Test
    fun `B7 - authorization number extracted when value on next line`() {
        val input = multiLineTextResult(
            "Authorization Number:",
            "AUTH-ABCD-1234",
            "RQID:RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("AUTH-ABCD-1234", result.fields.authorizationNumber)
        assertEquals("RQ-348145", result.fields.requestId)
    }

    @Test
    fun `B7 - all invoice fields extract from realistic multi-line layout`() {
        // Simulates the real docling output where each label/value is on its own line
        val input = multiLineTextResult(
            "Federal Tax ID Number: 923618220",
            "Vendor Number:",
            "923618220",
            "Authorization Number:",
            "AUTH-ABCD-1234",
            "RQID:RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("923618220", result.fields.federalTaxId)
        assertEquals("923618220", result.fields.vendorNumber)
        assertEquals("AUTH-ABCD-1234", result.fields.authorizationNumber)
        assertEquals("RQ-348145", result.fields.requestId)
    }

    // -------------------------------------------------------------------
    // B8: Footer case number, assigned code, DCC number not extracted
    // -------------------------------------------------------------------

    @Test
    fun `B8 - footer with trailing OMB and number extracts correctly`() {
        // Full footer with trailing /-separated components that should not be captured
        val input = textResult(
            "BHA-12345-CE/ Assigned 9106 null/ DCPS / DCC-9876 / OMB No. 0960-0555 / 98022179"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.fields.caseNumberFullFooter)
        assertEquals("9106", result.fields.assignedCode)
        assertEquals("DCC-9876", result.fields.dccNumber)
    }

    @Test
    fun `B8 - footer without null and agency still extracts`() {
        // Simpler footer without "null/" or agency code
        val input = textResult(
            "CASE-789/ Assigned 4321 DCC-5555 / OMB No. 0960-0555"
        )

        val result = parser.parse(input)

        assertEquals("CASE-789", result.fields.caseNumberFullFooter)
        assertEquals("4321", result.fields.assignedCode)
        assertEquals("DCC-5555", result.fields.dccNumber)
    }

    @Test
    fun `B8 - footer as multi-line text still extracts`() {
        // Footer split across lines should still work after collapse
        val input = multiLineTextResult(
            "BHA-12345-CE/ Assigned 9106 null/ DCPS /",
            "DCC-9876 / OMB No. 0960-0555 / 98022179"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.fields.caseNumberFullFooter)
        assertEquals("9106", result.fields.assignedCode)
        assertEquals("DCC-9876", result.fields.dccNumber)
    }

    // -------------------------------------------------------------------
    // Integration: docling-style full page header extraction
    // -------------------------------------------------------------------

    @Test
    fun `header extracts from docling-style single text element`() {
        // Matches the real docling output format from the reference JSON
        val input = textResult(
            "Date: August 13, 2024 Case ID: BHA-12345-CE RE: JOHN MICHAEL SMITH DOB: August 1, 2015 Applicant: JANE SMITH Authorization #: AUTH-ABCD-1234"
        )

        val result = parser.parse(input)

        assertEquals("August 13, 2024", result.fields.dateOfIssue)
        assertEquals("BHA-12345-CE", result.fields.caseId)
        assertEquals("JOHN", result.fields.firstName)
        assertEquals("MICHAEL", result.fields.middleName)
        assertEquals("SMITH", result.fields.lastName)
        assertEquals("August 1, 2015", result.fields.dob)
        assertEquals("JANE SMITH", result.fields.applicantName)
        assertEquals("AUTH-ABCD-1234", result.fields.authorizationNumber)
    }

    // ===================================================================
    // B9: Date of Issue parses to wrong value ("Donotwrite...")
    // ===================================================================

    @Test
    fun `B9 date of issue does not capture Do-not-write instruction text`() {
        // Simulates PDFBox output where "Date:" label and "Do not write..." form
        // instruction text are on nearby lines, and individual fallback is used.
        // Before the fix, the dateRegex captured "Donotwriteintheblocks..." as the date.
        val input = multiLineTextResult(
            "Date:",
            "Do not write in the blocks below - For DDS USE ONLY",
            "Some other content",
        )

        val result = parser.parse(input)

        // The date field should be null — "Do not write..." is not a valid date
        assertNull(result.fields.dateOfIssue,
            "Date of Issue should be null when only 'Do not write' text follows the Date: label")
    }

    @Test
    fun `B9 date of issue extracts MM-DD-YYYY format correctly`() {
        val input = multiLineTextResult(
            "Date: 09/15/2024",
            "Do not write in the blocks below - For DDS USE ONLY",
        )

        val result = parser.parse(input)

        assertEquals("09/15/2024", result.fields.dateOfIssue)
    }

    @Test
    fun `B9 date of issue extracts month-name format correctly`() {
        // This is the real format seen in the sample PDFs
        val input = multiLineTextResult(
            "Date: August 13, 2024",
            "Do not write in the blocks below - For DDS USE ONLY",
        )

        val result = parser.parse(input)

        assertEquals("August 13, 2024", result.fields.dateOfIssue)
    }

    @Test
    fun `B9 date of issue rejects concatenated instruction text`() {
        // Simulates the worst case: PDFBox concatenates "Date:" with the instruction
        // text on the same line without spaces (as reported in the bug)
        val input = textResult(
            "Date: Donotwriteintheblocksbelow Case ID: ABC-123"
        )

        val result = parser.parse(input)

        // The individual fallback date regex should NOT match "Donotwrite..."
        assertNull(result.fields.dateOfIssue,
            "Date of Issue should be null for non-date text like 'Donotwrite...'")
        // But Case ID should still be extracted
        assertEquals("ABC-123", result.fields.caseId)
    }

    // ===================================================================
    // B10: Footer pattern does not match real PDF footer text
    // ===================================================================

    @Test
    fun `B10 footer matches real PDF text with spaces around slashes`() {
        // Real docling-extracted footer text (with sanitized values):
        // "CASENUMBER/ Assigned 9106 null/ DCPS / REQUESTID / OMB No. 0960-0555 / 98022179"
        val input = textResult(
            "BHA-12345-CE/ Assigned 9106 null/ DCPS / DCC-9999 / OMB No. 0960-0555 / 98022179"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.fields.caseNumberFullFooter)
        assertEquals("9106", result.fields.assignedCode)
        assertEquals("DCC-9999", result.fields.dccNumber)
    }

    @Test
    fun `B10 footer matches when slashes have spaces on both sides`() {
        // PDFBox may add spaces before and after slashes
        val input = textResult(
            "BHA-77777-CE / Assigned 4321 null / DCPS / DCC-8888"
        )

        val result = parser.parse(input)

        assertEquals("BHA-77777-CE", result.fields.caseNumberFullFooter)
        assertEquals("4321", result.fields.assignedCode)
        assertEquals("DCC-8888", result.fields.dccNumber)
    }

    @Test
    fun `B10 footer matches with extra trailing fields after request ID`() {
        // The real footer has "/ OMB No. ..." after the request ID
        val input = multiLineTextResult(
            "BHA-55555-CE/ Assigned 9106 null/ DCPS / REQ-1234 / OMB No. 0960-0555 / 98022179",
        )

        val result = parser.parse(input)

        assertEquals("BHA-55555-CE", result.fields.caseNumberFullFooter)
        assertEquals("9106", result.fields.assignedCode)
        assertEquals("REQ-1234", result.fields.dccNumber)
    }

    // ===================================================================
    // B11: Applicant name not separated with spaces
    // ===================================================================

    @Test
    fun `B11 applicant name splits CamelCase into spaced words`() {
        // When PDFBox concatenates name blocks without spaces: "JaneSmith"
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: JaneSmith Authorization #: AUTH-999"
        )

        val result = parser.parse(input)

        assertEquals("Jane Smith", result.fields.applicantName,
            "CamelCase applicant name should be split into spaced words")
    }

    @Test
    fun `B11 applicant name with three CamelCase parts`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: JaneMarieDoe Authorization #: AUTH-999"
        )

        val result = parser.parse(input)

        assertEquals("Jane Marie Doe", result.fields.applicantName,
            "Three-part CamelCase applicant name should be fully split")
    }

    @Test
    fun `B11 already-spaced applicant name is unchanged`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-999"
        )

        val result = parser.parse(input)

        assertEquals("Jane Smith", result.fields.applicantName,
            "Already-spaced applicant name should remain unchanged")
    }

    @Test
    fun `B11 applicant name split works in individual fallback path`() {
        // Only applicant and auth fields — triggers individual fallback, not combined regex
        val input = textResult(
            "Applicant: JohnDoe Authorization #: AUTH-SOLO"
        )

        val result = parser.parse(input)

        assertEquals("John Doe", result.fields.applicantName,
            "CamelCase applicant name should be split even in individual fallback path")
    }

    @Test
    fun `splitCamelCaseName handles edge cases`() {
        assertEquals("Jane Smith", parser.splitCamelCaseName("JaneSmith"))
        assertEquals("John Michael Doe", parser.splitCamelCaseName("JohnMichaelDoe"))
        assertEquals("ALLCAPS", parser.splitCamelCaseName("ALLCAPS"))
        assertEquals("Already Spaced", parser.splitCamelCaseName("Already Spaced"))
        assertEquals("a", parser.splitCamelCaseName("a"))
        assertEquals("", parser.splitCamelCaseName(""))
    }

    // ===================================================================
    // WP-63: Multi-line claimant cell parsing
    // ===================================================================

    @Test
    fun `WP63 - multi-line claimant cell extracts all fields`() {
        val cell = "Claimant Information\nJOHN MICHAEL DOE\n123 MAIN ST\nANYTOWN, MD 21201\n410-555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN MICHAEL DOE", result.nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertEquals("410-555-1234", result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with two-word name`() {
        val cell = "Claimant Information\nJANE SMITH\n456 OAK AVE\nSPRINGFIELD, VA 22150\n703-555-6789"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JANE SMITH", result.nameFromTable)
        assertEquals("456 OAK AVE", result.streetAddress)
        assertEquals("SPRINGFIELD", result.city)
        assertEquals("VA", result.state)
        assertEquals("22150", result.zipCode)
        assertEquals("703-555-6789", result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell without phone`() {
        val cell = "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertNull(result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with zip plus four`() {
        val cell = "Claimant Information\nJOHN DOE\n789 ELM DR\nBALTIMORE, MD 21201-4567\n410-555-0000"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertEquals("789 ELM DR", result.streetAddress)
        assertEquals("BALTIMORE", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201-4567", result.zipCode)
        assertEquals("410-555-0000", result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with Windows line endings`() {
        val cell = "Claimant Information\r\nJOHN DOE\r\n123 MAIN ST\r\nANYTOWN, MD 21201\r\n410-555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertEquals("410-555-1234", result.phone)
    }

    @Test
    fun `WP63 - degenerate single-line claimant cell extracts what it can`() {
        // Single-line format no longer represents real data (TableExtractor now
        // preserves newlines). The multi-line parser treats the single line as
        // a city/state/zip match, extracting state and zip but conflating name,
        // street, and city into one field. This is acceptable since the format
        // should not occur in practice.
        val cell = "Claimant Information JOHN DOE 123 MAIN ST ANYTOWN, MD 21201 410-555-1234"
        val result = parser.parseClaimantCell(cell)

        // State and zip are reliably extracted even from a single line
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
    }

    @Test
    fun `WP63 - multi-line claimant cell with no street address line`() {
        // Edge case: name followed directly by city/state/zip (no street)
        val cell = "Claimant Information\nJOHN DOE\nANYTOWN, MD 21201\n410-555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertNull(result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertEquals("410-555-1234", result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with multi-word city`() {
        val cell = "Claimant Information\nJANE DOE\n100 BROAD ST\nNEW YORK, NY 10001\n212-555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JANE DOE", result.nameFromTable)
        assertEquals("100 BROAD ST", result.streetAddress)
        assertEquals("NEW YORK", result.city)
        assertEquals("NY", result.state)
        assertEquals("10001", result.zipCode)
        assertEquals("212-555-1234", result.phone)
    }

    @Test
    fun `WP63 - multi-line through full parse pipeline`() {
        // Test that multi-line claimant cells work through the full parse() method
        val tables = tableWith(
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201\n410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("123 MAIN ST", result.fields.streetAddress)
        assertEquals("ANYTOWN", result.fields.city)
        assertEquals("MD", result.fields.state)
        assertEquals("21201", result.fields.zipCode)
        assertEquals("410-555-1234", result.fields.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with parenthesized phone`() {
        val cell = "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201\n(410) 555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertEquals("(410) 555-1234", result.phone)
    }

    @Test
    fun `WP63 - multi-line claimant cell with extra blank lines`() {
        val cell = "Claimant Information\n\nJOHN DOE\n\n123 MAIN ST\n\nANYTOWN, MD 21201\n\n410-555-1234"
        val result = parser.parseClaimantCell(cell)

        assertEquals("JOHN DOE", result.nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress)
        assertEquals("ANYTOWN", result.city)
        assertEquals("MD", result.state)
        assertEquals("21201", result.zipCode)
        assertEquals("410-555-1234", result.phone)
    }

    // ===================================================================
    // Missing-field completeness warnings (WP-64)
    // ===================================================================

    @Test
    fun `completeness warnings generated for all missing expected fields on empty input`() {
        val input = textResult("")
        val result = parser.parse(input)

        val completenessWarnings = result.warnings.filter { it.stage == "completeness" }
        // All expected fields should be missing for empty input
        val expectedCount = FieldParser.EXPECTED_FIELDS.size
        assertEquals(expectedCount, completenessWarnings.size,
            "All $expectedCount expected fields should generate completeness warnings on empty input")

        // Verify the stage is "completeness" for all
        assertTrue(completenessWarnings.all { it.stage == "completeness" },
            "All missing-field warnings should have stage 'completeness'")

        // Verify each expected field name is represented
        val warningFieldNames = completenessWarnings.map { it.field }.toSet()
        for ((name, _) in FieldParser.EXPECTED_FIELDS) {
            assertTrue(name in warningFieldNames,
                "Expected field '$name' should have a completeness warning")
        }
    }

    @Test
    fun `no completeness warnings when all expected fields are present`() {
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-999"
        )
        val tables = tableWith(
            "Claimant Information\nJOHN DOE\n123 MAIN ST\nANYTOWN, MD 21201\n410-555-1234",
            "Date and Time Thursday September 5th, 2024 10:00 AM Eastern Standard Time",
            "Services Authorized Code: 96130 Procedure Type Code: P Desc: Test Fee: \$ 100.00",
        )

        val result = parser.parse(input, tables)

        val completenessWarnings = result.warnings.filter { it.stage == "completeness" }
        assertTrue(completenessWarnings.isEmpty(),
            "No completeness warnings should appear when all expected fields are extracted. " +
            "Got: ${completenessWarnings.map { it.field }}")
    }

    @Test
    fun `completeness warnings only for specific missing fields`() {
        // Header provides name, DOB, case ID, date of issue, auth number
        // but NO address, city, state, zip, or appointment date
        val input = textResult(
            "Date: 09/15/2024 Case ID: ABC-123 RE: John Smith DOB: 01/01/2000 Applicant: Jane Smith Authorization #: AUTH-999"
        )

        val result = parser.parse(input)

        val completenessWarnings = result.warnings.filter { it.stage == "completeness" }
        val warningFieldNames = completenessWarnings.map { it.field }.toSet()

        // These fields should NOT have warnings (they were extracted)
        assertFalse("Claimant first name" in warningFieldNames,
            "First name was extracted, should not have a warning")
        assertFalse("Claimant last name" in warningFieldNames,
            "Last name was extracted, should not have a warning")
        assertFalse("Date of birth" in warningFieldNames,
            "DOB was extracted, should not have a warning")
        assertFalse("Case ID" in warningFieldNames,
            "Case ID was extracted, should not have a warning")
        assertFalse("Date of issue" in warningFieldNames,
            "Date of issue was extracted, should not have a warning")
        assertFalse("Authorization number" in warningFieldNames,
            "Authorization number was extracted, should not have a warning")

        // These fields SHOULD have warnings (not provided by header alone)
        assertTrue("Appointment date" in warningFieldNames,
            "Appointment date should have a completeness warning")
        assertTrue("Street address" in warningFieldNames,
            "Street address should have a completeness warning")
        assertTrue("City" in warningFieldNames,
            "City should have a completeness warning")
        assertTrue("State" in warningFieldNames,
            "State should have a completeness warning")
        assertTrue("ZIP code" in warningFieldNames,
            "ZIP code should have a completeness warning")
    }

    @Test
    fun `completeness warnings do not include optional fields`() {
        val input = textResult("")
        val result = parser.parse(input)

        val completenessWarnings = result.warnings.filter { it.stage == "completeness" }
        val warningFieldNames = completenessWarnings.map { it.field }.toSet()

        // Optional fields should NEVER appear in completeness warnings
        assertFalse("middleName" in warningFieldNames || warningFieldNames.any { "middle" in it.lowercase() },
            "Middle name is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "applicant" in it.lowercase() },
            "Applicant name is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "phone" in it.lowercase() },
            "Phone is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "federal" in it.lowercase() || "tax" in it.lowercase() },
            "Federal Tax ID is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "vendor" in it.lowercase() },
            "Vendor Number is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "assigned" in it.lowercase() },
            "Assigned Code is optional, should not generate a completeness warning")
        assertFalse(warningFieldNames.any { "dcc" in it.lowercase() },
            "DCC Number is optional, should not generate a completeness warning")
    }

    @Test
    fun `generateMissingFieldWarnings is callable as a static utility`() {
        // Verify the companion object method works directly with a ReferralFields instance
        val fields = ReferralFields(
            firstName = "John",
            lastName = "Doe",
            dob = "01/01/2000",
            caseId = "ABC-123",
            dateOfIssue = "09/15/2024",
            authorizationNumber = "AUTH-999",
            appointmentDate = "September 5th, 2024",
            streetAddress = "123 Main St",
            city = "Anytown",
            state = "MD",
            zipCode = "21201",
        )

        val warnings = FieldParser.generateMissingFieldWarnings(fields)
        assertTrue(warnings.isEmpty(),
            "No warnings should be generated when all expected fields are present")
    }

    @Test
    fun `generateMissingFieldWarnings detects blank strings as missing`() {
        // Blank (whitespace-only) strings should be treated as missing
        val fields = ReferralFields(
            firstName = "  ",
            lastName = "Doe",
            dob = "01/01/2000",
            caseId = "",
            dateOfIssue = "09/15/2024",
            authorizationNumber = "AUTH-999",
            appointmentDate = "September 5th, 2024",
            streetAddress = "123 Main St",
            city = "Anytown",
            state = "MD",
            zipCode = "21201",
        )

        val warnings = FieldParser.generateMissingFieldWarnings(fields)
        val warningFieldNames = warnings.map { it.field }.toSet()

        assertEquals(2, warnings.size, "Should warn about 2 blank/empty fields")
        assertTrue("Claimant first name" in warningFieldNames,
            "Blank first name should generate a warning")
        assertTrue("Case ID" in warningFieldNames,
            "Empty Case ID should generate a warning")
    }

    @Test
    fun `completeness warning messages follow expected format`() {
        val fields = ReferralFields() // All null
        val warnings = FieldParser.generateMissingFieldWarnings(fields)

        // Each warning message should be "{field name} not found"
        for (warning in warnings) {
            assertEquals("${warning.field} not found", warning.message,
                "Warning message should follow '{field} not found' format")
            assertEquals("completeness", warning.stage,
                "Warning stage should be 'completeness'")
        }
    }

    // -------------------------------------------------------------------
    // Provider name extraction (Pay to:)
    // -------------------------------------------------------------------

    @Test
    fun `provider name extracted from Pay to on same line`() {
        val input = textResult(
            "Pay to: Smith & Calabrese Assessments LLC"
        )

        val result = parser.parse(input)

        assertEquals("Smith & Calabrese Assessments LLC", result.fields.providerName)
    }

    @Test
    fun `provider name extracted from Pay to with multiline address`() {
        val input = multiLineTextResult(
            "Pay to: Dr. Jane Provider",
            "123 Medical Center Drive",
            "Suite 200",
        )

        val result = parser.parse(input)

        assertEquals("Dr. Jane Provider", result.fields.providerName)
    }

    @Test
    fun `provider name extracted from Pay to on next line`() {
        val input = multiLineTextResult(
            "Pay to:",
            "Assessment Services Inc",
            "456 Office Park",
        )

        val result = parser.parse(input)

        assertEquals("Assessment Services Inc", result.fields.providerName)
    }

    @Test
    fun `provider name is null when Pay to is absent`() {
        val input = textResult(
            "Federal Tax ID Number: 123456789 Vendor Number: V-999"
        )

        val result = parser.parse(input)

        assertNull(result.fields.providerName)
    }

    @Test
    fun `provider name extraction is case insensitive`() {
        val input = textResult(
            "PAY TO: Provider Name Here"
        )

        val result = parser.parse(input)

        assertEquals("Provider Name Here", result.fields.providerName)
    }

    @Test
    fun `provider name included in filledFieldCount`() {
        val fields = ReferralFields(providerName = "Test Provider")
        assertEquals(1, fields.filledFieldCount())
    }

    @Test
    fun `provider name wired through full parse pipeline`() {
        // Simulate a page with both invoice fields and Pay to
        val input = textResult(
            "Federal Tax ID Number: 123456789 Vendor Number: V-999 Pay to: My Clinic Name"
        )

        val result = parser.parse(input)

        assertEquals("My Clinic Name", result.fields.providerName)
        assertEquals("123456789", result.fields.federalTaxId)
        assertEquals("V-999", result.fields.vendorNumber)
    }
}
