package tech.carbonworks.snc.batchreferralparser.extraction

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [FieldParser] covering all extraction methods ported from the
 * Python prototype `extract_referral_fields.py`.
 *
 * Tests construct [ExtractionResult.Success] and [ExtractedTable] objects
 * programmatically with known text content (no real PDFs or PHI needed).
 */
class FieldParserTest {

    private val parser = FieldParser()

    // -------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------

    /**
     * Build an [ExtractionResult.Success] from one or more page text strings.
     * Each string becomes a page with word-level TextBlocks (split on whitespace).
     * All words are placed at the same Y coordinate (single line).
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

        assertEquals("09/15/2024", result.dateOfIssue.value)
        assertEquals(Confidence.HIGH, result.dateOfIssue.confidence)
        assertEquals("ABC-12345", result.caseId.value)
        assertEquals("John", result.firstName.value)
        assertEquals("Michael", result.middleName.value)
        assertEquals("Smith", result.lastName.value)
        assertEquals("03/22/1990", result.dob.value)
        assertEquals("Jane Smith", result.applicantName.value)
        assertEquals("AUTH-9876", result.authorizationNumber.value)
        assertEquals(Confidence.HIGH, result.authorizationNumber.confidence)
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

        assertEquals("Alice", result.firstName.value)
        assertNull(result.middleName.value)
        assertEquals("Johnson", result.lastName.value)
        assertEquals("XYZ-999", result.caseId.value)
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

        assertEquals("BHA-12345-CE", result.caseNumberFullFooter.value)
        assertEquals(Confidence.HIGH, result.caseNumberFullFooter.confidence)
        assertEquals("4321", result.assignedCode.value)
        assertEquals("DCC-9999", result.dccNumber.value)
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

        assertEquals("123 MAIN ST", result.streetAddress.value)
        assertEquals(Confidence.MEDIUM, result.streetAddress.confidence)
        assertEquals("ANYTOWN", result.city.value)
        assertEquals("MD", result.state.value)
        assertEquals("21201", result.zipCode.value)
        assertEquals("410-555-1234", result.phone.value)
        assertEquals(Confidence.MEDIUM, result.phone.confidence)
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

        assertEquals("Thursday September 5th, 2024", result.appointmentDate.value)
        assertEquals(Confidence.MEDIUM, result.appointmentDate.confidence)
        assertEquals("10:00 AM", result.appointmentTime.value)
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

        assertEquals(2, result.services.size)
        assertEquals(Confidence.HIGH, result.servicesConfidence)

        val svc1 = result.services[0]
        assertEquals("96130", svc1.cptCode)
        assertEquals("P", svc1.procedureTypeCode)
        assertEquals("Psychological testing evaluation", svc1.description)
        assertEquals("225.00", svc1.fee)

        val svc2 = result.services[1]
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

        assertEquals("123456789", result.federalTaxId.value)
        assertEquals(Confidence.MEDIUM, result.federalTaxId.confidence)
        assertEquals("V-5678", result.vendorNumber.value)
        assertEquals("RQ-42", result.requestId.value)
        // Invoice auth number should be overridden if header also has one,
        // but with no header it should be the invoice value
        assertEquals("AUTH-INVOICE-1", result.authorizationNumber.value)
        assertEquals(Confidence.MEDIUM, result.authorizationNumber.confidence)
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

        assertEquals("410-555-9876", result.phone.value)
        assertEquals(Confidence.LOW, result.phone.confidence)
    }

    // -------------------------------------------------------------------
    // 9. Missing fields produce notFound (empty input)
    // -------------------------------------------------------------------

    @Test
    fun `empty input produces all notFound fields`() {
        val input = textResult("")

        val result = parser.parse(input)

        assertFalse(result.firstName.isPresent)
        assertFalse(result.lastName.isPresent)
        assertFalse(result.caseId.isPresent)
        assertFalse(result.authorizationNumber.isPresent)
        assertFalse(result.dob.isPresent)
        assertFalse(result.appointmentDate.isPresent)
        assertFalse(result.phone.isPresent)
        assertFalse(result.federalTaxId.isPresent)
        assertNull(result.firstName.confidence)
        assertTrue(result.services.isEmpty())
        assertNull(result.servicesConfidence)
        assertEquals(0, result.filledFieldCount())
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
        assertEquals("AUTH-HEADER", result.authorizationNumber.value)
        assertEquals(Confidence.HIGH, result.authorizationNumber.confidence)
    }

    // -------------------------------------------------------------------
    // 11. Low confidence detection and filledFieldCount
    // -------------------------------------------------------------------

    @Test
    fun `hasLowConfidenceFields and filledFieldCount work correctly`() {
        // Only a CELL # phone (LOW confidence) and one table cell (MEDIUM confidence)
        val input = textResult("CELL # 555-123-4567")
        val tables = tableWith(
            "Date and Time Monday January 6th, 2025 2:30 PM Eastern"
        )

        val result = parser.parse(input, tables)

        // Phone is LOW confidence from CELL # pattern
        assertTrue(result.hasLowConfidenceFields())
        assertEquals(Confidence.LOW, result.phone.confidence)

        // Should have: phone (1) + appointmentDate (1) + appointmentTime (1) = 3 fields
        assertEquals(3, result.filledFieldCount())
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

        assertEquals("John", result.firstName.value)
        assertEquals("Michael", result.middleName.value)
        assertEquals("Smith", result.lastName.value)
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

        assertEquals("JOHN", result.firstName.value)
        assertEquals("MICHAEL", result.middleName.value)
        assertEquals("SMITH", result.lastName.value)
        assertEquals("BHA-12345-CE", result.caseId.value)
        assertEquals("AUTH-ABCD-1234", result.authorizationNumber.value)
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

        assertEquals("BHA-12345-CE", result.caseId.value)
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

        assertEquals("BHA-99999-CE", result.caseId.value)
        // Fallback extraction gives LOW confidence
        assertEquals(Confidence.LOW, result.caseId.confidence)
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

        assertEquals("RQ-348145", result.requestId.value)
    }

    @Test
    fun `B4 - RQID extracted when concatenated with no space`() {
        // Docling shows "RQID:RQ-348145" as a single text element
        val input = textResult(
            "RQID:RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("RQ-348145", result.requestId.value)
    }

    @Test
    fun `B4 - RQID extracted with space between colon and value`() {
        val input = textResult(
            "RQID: RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("RQ-348145", result.requestId.value)
    }

    // -------------------------------------------------------------------
    // B6: Street address not extracted when state and zip concatenated
    // -------------------------------------------------------------------

    @Test
    fun `B6 - claimant cell with no space between state and zip`() {
        val tables = tableWith(
            "Claimant Information JOHN DOE 123 MAIN ST ANYTOWN, MD21201 410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("JOHN DOE", parser.parseClaimantCell(
            "Claimant Information JOHN DOE 123 MAIN ST ANYTOWN, MD21201 410-555-1234"
        ).nameFromTable)
        assertEquals("123 MAIN ST", result.streetAddress.value)
        assertEquals("ANYTOWN", result.city.value)
        assertEquals("MD", result.state.value)
        assertEquals("21201", result.zipCode.value)
        assertEquals("410-555-1234", result.phone.value)
    }

    @Test
    fun `B6 - claimant cell with three-part name and no space state-zip`() {
        val tables = tableWith(
            "Claimant Information JOHN MICHAEL SMITH 123 MAIN ST ANYTOWN, MD21201 410-555-1234"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("123 MAIN ST", result.streetAddress.value)
        assertEquals("ANYTOWN", result.city.value)
        assertEquals("MD", result.state.value)
        assertEquals("21201", result.zipCode.value)
        assertEquals("410-555-1234", result.phone.value)
    }

    @Test
    fun `B6 - claimant cell with zip plus four`() {
        val tables = tableWith(
            "Claimant Information JOHN DOE 456 OAK AVE SPRINGFIELD, VA22150-1234 703-555-6789"
        )
        val input = textResult("")

        val result = parser.parse(input, tables)

        assertEquals("456 OAK AVE", result.streetAddress.value)
        assertEquals("SPRINGFIELD", result.city.value)
        assertEquals("VA", result.state.value)
        assertEquals("22150-1234", result.zipCode.value)
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

        assertEquals("923618220", result.federalTaxId.value)
        assertEquals("V-5678", result.vendorNumber.value)
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

        assertEquals("V-5678", result.vendorNumber.value)
        assertEquals("AUTH-ABCD-1234", result.authorizationNumber.value)
    }

    @Test
    fun `B7 - authorization number extracted when value on next line`() {
        val input = multiLineTextResult(
            "Authorization Number:",
            "AUTH-ABCD-1234",
            "RQID:RQ-348145"
        )

        val result = parser.parse(input)

        assertEquals("AUTH-ABCD-1234", result.authorizationNumber.value)
        assertEquals("RQ-348145", result.requestId.value)
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

        assertEquals("923618220", result.federalTaxId.value)
        assertEquals("923618220", result.vendorNumber.value)
        assertEquals("AUTH-ABCD-1234", result.authorizationNumber.value)
        assertEquals("RQ-348145", result.requestId.value)
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

        assertEquals("BHA-12345-CE", result.caseNumberFullFooter.value)
        assertEquals("9106", result.assignedCode.value)
        assertEquals("DCC-9876", result.dccNumber.value)
    }

    @Test
    fun `B8 - footer without null and agency still extracts`() {
        // Simpler footer without "null/" or agency code
        val input = textResult(
            "CASE-789/ Assigned 4321 DCC-5555 / OMB No. 0960-0555"
        )

        val result = parser.parse(input)

        assertEquals("CASE-789", result.caseNumberFullFooter.value)
        assertEquals("4321", result.assignedCode.value)
        assertEquals("DCC-5555", result.dccNumber.value)
    }

    @Test
    fun `B8 - footer as multi-line text still extracts`() {
        // Footer split across lines should still work after collapse
        val input = multiLineTextResult(
            "BHA-12345-CE/ Assigned 9106 null/ DCPS /",
            "DCC-9876 / OMB No. 0960-0555 / 98022179"
        )

        val result = parser.parse(input)

        assertEquals("BHA-12345-CE", result.caseNumberFullFooter.value)
        assertEquals("9106", result.assignedCode.value)
        assertEquals("DCC-9876", result.dccNumber.value)
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

        assertEquals("August 13, 2024", result.dateOfIssue.value)
        assertEquals("BHA-12345-CE", result.caseId.value)
        assertEquals("JOHN", result.firstName.value)
        assertEquals("MICHAEL", result.middleName.value)
        assertEquals("SMITH", result.lastName.value)
        assertEquals("August 1, 2015", result.dob.value)
        assertEquals("JANE SMITH", result.applicantName.value)
        assertEquals("AUTH-ABCD-1234", result.authorizationNumber.value)
    }
}
