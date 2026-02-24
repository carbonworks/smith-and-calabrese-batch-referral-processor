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
}
