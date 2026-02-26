package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Extracts structured referral fields from raw PDF extraction results.
 *
 * Ports the Python prototype logic from `extract_referral_fields.py` to Kotlin.
 * Takes [ExtractionResult.Success] (word-level text blocks) and [ExtractedTable] data,
 * reconstructs page-level text strings, applies regex patterns and heuristics to
 * extract each target field, and returns a populated [ReferralFields] with per-field
 * confidence scores.
 *
 * Merge priority: header > table > invoice > fallback (phone).
 */
class FieldParser {

    companion object {
        /** Y-coordinate tolerance for grouping text blocks on the same line. */
        private const val LINE_Y_TOLERANCE = 3f
    }

    /**
     * Parse all referral fields from the given extraction result and tables.
     *
     * @param textResult successful PDF text extraction containing word-level text blocks
     * @param tables structured table data extracted from the PDF
     * @return populated [ReferralFields] with confidence scores for each extracted field
     */
    fun parse(
        textResult: ExtractionResult.Success,
        tables: List<ExtractedTable> = emptyList(),
    ): ReferralFields {
        // Reconstruct page-level text strings from word-level TextBlocks.
        // Group blocks by Y-coordinate into lines, then join lines with newlines.
        val pageTexts = textResult.pages.map { page ->
            reconstructPageText(page.textBlocks)
        }

        // Extract from each source
        val headerFields = extractHeaderBlock(pageTexts)
        val caseFields = extractCaseNumberComponents(pageTexts)
        val tableFields = extractTableFields(tables)
        val invoiceFields = extractInvoiceFields(pageTexts)
        val phoneFromCell = extractPhone(pageTexts)

        // Individual fallback extraction across concatenated all-pages text
        // for fields that may not be found by their primary extractor
        val allText = pageTexts.joinToString("\n")
        val fallbackFields = extractFallbackFields(allText, headerFields, invoiceFields)

        // Merge with priority: header > table > invoice > fallback
        // Start with lowest priority and override with higher priority sources
        return mergeFields(invoiceFields, tableFields, headerFields, caseFields, phoneFromCell, fallbackFields)
    }

    /**
     * Reconstruct page text from text blocks, grouping by Y-coordinate into lines.
     *
     * Blocks within [LINE_Y_TOLERANCE] of each other are considered the same line
     * and joined with spaces. Different lines are separated by newlines. This preserves
     * the line structure of the original PDF layout, which is critical for cross-line
     * regex matching.
     */
    internal fun reconstructPageText(textBlocks: List<TextBlock>): String {
        if (textBlocks.isEmpty()) return ""

        // Sort blocks by Y coordinate first (top to bottom), then X (left to right)
        val sorted = textBlocks.sortedWith(compareBy({ it.boundingBox.y }, { it.boundingBox.x }))

        val lines = mutableListOf<MutableList<TextBlock>>()
        var currentLine = mutableListOf(sorted.first())
        var currentY = sorted.first().boundingBox.y

        for (i in 1 until sorted.size) {
            val block = sorted[i]
            if (kotlin.math.abs(block.boundingBox.y - currentY) <= LINE_Y_TOLERANCE) {
                currentLine.add(block)
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(block)
                currentY = block.boundingBox.y
            }
        }
        lines.add(currentLine)

        return lines.joinToString("\n") { line ->
            line.joinToString(" ") { it.text }
        }
    }

    // -----------------------------------------------------------------------
    // Header block extraction
    // -----------------------------------------------------------------------

    /**
     * Extract fields from the appointment authorization header block.
     *
     * Looks for the pattern:
     *   Date: ... Case ID: ... RE: ... DOB: ... Applicant: ... Authorization #: ...
     *
     * The header regex uses [\s\S] to handle cross-line matches when text blocks
     * are reconstructed with newlines between different Y-coordinate groups.
     */
    internal fun extractHeaderBlock(pageTexts: List<String>): HeaderFields {
        // Use [\s\S] instead of . so the pattern matches across newlines
        val headerRegex = Regex(
            """Date:[\s\S]*?(\S[\s\S]*?)\s*Case\s+ID:\s*(\S+)[\s\S]*?RE:\s*([\s\S]*?)\s*DOB:\s*([\s\S]*?)\s*Applicant:\s*([\s\S]*?)\s*Authorization\s*#:\s*(\S+)"""
        )

        for (text in pageTexts) {
            if ("Case" !in text || "Authorization" !in text) continue

            // Collapse the page text to a single line for header matching,
            // since the header is logically one block but may span multiple Y-coords
            val collapsed = text.replace('\n', ' ').replace(Regex("\\s+"), " ")

            val match = headerRegex.find(collapsed) ?: continue

            val dateOfIssue = match.groupValues[1].trim()
            val caseId = match.groupValues[2].trim()
            val reName = match.groupValues[3].trim()
            val dob = match.groupValues[4].trim()
            val applicantName = match.groupValues[5].trim()
            val authorizationNumber = match.groupValues[6].trim()

            // Parse RE: field into name components
            val nameResult = parseNameParts(reName)

            return HeaderFields(
                dateOfIssue = dateOfIssue,
                caseId = caseId,
                firstName = nameResult.first,
                middleName = nameResult.second,
                lastName = nameResult.third,
                dob = dob,
                applicantName = applicantName,
                authorizationNumber = authorizationNumber,
            )
        }

        return HeaderFields()
    }

    /**
     * Parse a name string into (firstName, middleName, lastName) components.
     *
     * Handles camelCase-like concatenation where PDFBox merges name parts without
     * spaces (e.g., "JohnMichaelSmith") by inserting spaces before uppercase letters
     * that follow lowercase letters.
     */
    internal fun parseNameParts(reName: String): Triple<String?, String?, String?> {
        // If the name has no spaces but has internal uppercase transitions
        // (e.g., "JohnMichaelSmith"), insert spaces before each uppercase
        // letter following a lowercase letter.
        val normalized = if (" " !in reName && reName.length > 1) {
            splitCamelCaseName(reName)
        } else {
            reName
        }

        val nameParts = normalized.split(Regex("\\s+"))
        return when {
            nameParts.size >= 3 -> Triple(
                nameParts[0],
                nameParts.subList(1, nameParts.size - 1).joinToString(" "),
                nameParts.last(),
            )
            nameParts.size == 2 -> Triple(nameParts[0], null, nameParts[1])
            else -> Triple(normalized, null, null)
        }
    }

    /**
     * Insert spaces before uppercase letters that follow a lowercase letter.
     * Handles camelCase-like name concatenation from PDFBox.
     *
     * Example: "JohnMichaelSmith" -> "John Michael Smith"
     */
    internal fun splitCamelCaseName(name: String): String {
        return name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }

    // -----------------------------------------------------------------------
    // Case number components (footer pattern)
    // -----------------------------------------------------------------------

    /**
     * Extract case number components from footer text.
     *
     * Looks for: CASE-NUMBER/ Assigned NNNN [null/] [AGENCY /] DCC-NUMBER [/ trailing...]
     *
     * The footer may contain trailing /-separated components like OMB numbers that
     * should not be captured. The regex stops the DCC capture at the next / or end of text.
     */
    internal fun extractCaseNumberComponents(pageTexts: List<String>): CaseFields {
        // More robust footer regex:
        // 1. Case number: non-slash token before "/ Assigned"
        // 2. Assigned code: digits after "Assigned"
        // 3. Optional "null/" and optional agency code + "/"
        // 4. DCC number: next non-slash, non-whitespace token, stopping before "/" or end
        val footerRegex = Regex(
            """(\S+)/\s*Assigned\s+(\d+)\s+(?:null/\s*)?(?:[A-Z]+\s*/\s*)?(\S+?)(?:\s*/|\s*$)"""
        )

        for (text in pageTexts) {
            // Collapse newlines for footer matching since the footer is one logical line
            val collapsed = text.replace('\n', ' ')
            val match = footerRegex.find(collapsed)
            if (match != null) {
                return CaseFields(
                    caseNumberFullFooter = match.groupValues[1].trim(),
                    assignedCode = match.groupValues[2].trim(),
                    dccNumber = match.groupValues[3].trim(),
                )
            }
        }

        return CaseFields()
    }

    // -----------------------------------------------------------------------
    // Table field extraction
    // -----------------------------------------------------------------------

    /**
     * Extract fields from the appointment authorization table.
     *
     * The table has 3 key cells identified by content prefix:
     * - "Claimant Information" cell: name, address, city, state, zip, phone
     * - "Date and Time" cell: appointment date and time
     * - "Services Authorized" or "Code:" cell: CPT codes, descriptions, fees
     */
    internal fun extractTableFields(tables: List<ExtractedTable>): TableFields {
        var result = TableFields()

        for (table in tables) {
            for (cell in table.cells) {
                val cellText = cell.content

                when {
                    cellText.startsWith("Claimant Information") -> {
                        result = result.mergeClaimant(parseClaimantCell(cellText))
                    }
                    "Date and Time" in cellText -> {
                        result = result.mergeAppointment(parseAppointmentCell(cellText))
                    }
                    "Services Authorized" in cellText || "Code:" in cellText -> {
                        val services = parseServicesCell(cellText)
                        if (services.isNotEmpty()) {
                            result = result.copy(
                                services = services,
                                servicesConfidence = Confidence.HIGH,
                            )
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Parse the claimant information cell.
     *
     * Expected format:
     *   Claimant Information FIRST MIDDLE LAST 123 STREET CITY, ST 12345 555-123-4567
     */
    internal fun parseClaimantCell(text: String): ClaimantInfo {
        var remaining = text.removePrefix("Claimant Information").trim()

        // Extract phone (at the end)
        var phone: String? = null
        val phoneRegex = Regex("""\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}\s*$""")
        val phoneMatch = phoneRegex.find(remaining)
        if (phoneMatch != null) {
            phone = phoneMatch.value.trim()
            remaining = remaining.substring(0, phoneMatch.range.first).trim()
        }

        // Extract city, state, zip pattern
        var city: String? = null
        var state: String? = null
        var zipCode: String? = null
        var streetAddress: String? = null
        var nameFromTable: String? = null

        val addrRegex = Regex("""(.+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)""")
        val addrMatch = addrRegex.find(remaining)
        if (addrMatch != null) {
            val cityField = addrMatch.groupValues[1].trim()

            // The city field (group 1) captures everything before the comma,
            // including the name, street address, and city name. The actual city
            // is the last word; everything before it is name + street address.
            val cityWords = cityField.split(Regex("\\s+"))
            city = cityWords.lastOrNull()
            state = addrMatch.groupValues[2].trim()
            zipCode = addrMatch.groupValues[3].trim()

            // Everything in group 1 except the last word (city) is name + street
            val beforeCityWords = if (cityWords.size > 1) {
                cityWords.subList(0, cityWords.size - 1)
            } else {
                emptyList()
            }

            // Separate name from street address
            if (beforeCityWords.size >= 4) {
                // Heuristic: street addresses start with a digit
                val streetStartIndex = beforeCityWords
                    .indexOfFirst { it.isNotEmpty() && it[0].isDigit() }
                if (streetStartIndex > 0) {
                    nameFromTable = beforeCityWords
                        .subList(0, streetStartIndex).joinToString(" ")
                    streetAddress = beforeCityWords
                        .subList(streetStartIndex, beforeCityWords.size).joinToString(" ")
                } else {
                    nameFromTable = beforeCityWords.joinToString(" ")
                }
            } else if (beforeCityWords.isNotEmpty()) {
                nameFromTable = beforeCityWords.joinToString(" ")
            }
        }

        return ClaimantInfo(
            nameFromTable = nameFromTable,
            streetAddress = streetAddress,
            city = city,
            state = state,
            zipCode = zipCode,
            phone = phone,
        )
    }

    /**
     * Parse the appointment date and time cell.
     *
     * Looks for patterns like "Thursday September 5th, 2024" and "10:00 AM".
     */
    internal fun parseAppointmentCell(text: String): AppointmentInfo {
        val remaining = text.replace("Date and Time", "").trim()

        // Date: weekday + month + day + year
        val dateRegex = Regex(
            """((?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\s+\w+\s+\d{1,2}(?:st|nd|rd|th)?,?\s*\d{4})"""
        )
        val dateMatch = dateRegex.find(remaining)

        // Time: HH:MM AM/PM
        val timeRegex = Regex("""(\d{1,2}:\d{2}\s*(?:AM|PM))""")
        val timeMatch = timeRegex.find(remaining)

        return AppointmentInfo(
            appointmentDate = dateMatch?.groupValues?.get(1)?.trim(),
            appointmentTime = timeMatch?.groupValues?.get(1)?.trim(),
        )
    }

    /**
     * Parse the services authorized cell into service line items.
     *
     * Each service starts with "Code:" and may include procedure type, description, and fee.
     * Pattern: Code: {cpt} [Procedure Type Code: {type}] [Desc: {desc}] [Fee: ${amount}]
     */
    internal fun parseServicesCell(text: String): List<ServiceLine> {
        val services = mutableListOf<ServiceLine>()

        // Split on each "Code:" that is NOT preceded by "Type " (to avoid splitting
        // on "Procedure Type Code:"). Uses a negative lookbehind.
        val chunks = text.split(Regex("""(?<!\bType )(?=Code:\s*)"""))

        for (chunk in chunks) {
            val trimmed = chunk.trim()
            if (!trimmed.startsWith("Code:")) continue

            val codeMatch = Regex("""Code:\s*(\S+)""").find(trimmed) ?: continue
            val cptCode = codeMatch.groupValues[1]

            val procMatch = Regex("""Procedure Type Code:\s*(\S+)""").find(trimmed)
            val descMatch = Regex("""Desc:\s*(.+?)(?=\s+Fee:|\s+Code:|$)""").find(trimmed)
            val feeMatch = Regex("""Fee:\s*\$\s*([\d,.]+)""").find(trimmed)

            services.add(
                ServiceLine(
                    cptCode = cptCode,
                    procedureTypeCode = procMatch?.groupValues?.get(1),
                    description = descMatch?.groupValues?.get(1)?.trim(),
                    fee = feeMatch?.groupValues?.get(1),
                )
            )
        }

        return services
    }

    // -----------------------------------------------------------------------
    // Invoice field extraction
    // -----------------------------------------------------------------------

    /**
     * Extract fields from the invoice section (typically page 3).
     *
     * Looks for: Federal Tax ID Number, Vendor Number, Authorization Number, RQID.
     *
     * Handles cross-line extraction where a label (e.g., "Vendor Number:") appears
     * on one line and the value on the next. Uses two strategies:
     * 1. Same-line regex with \s* that handles space-separated label:value
     * 2. Cross-line fallback that finds the label and captures the first non-empty
     *    token on the following line
     */
    internal fun extractInvoiceFields(pageTexts: List<String>): InvoiceFields {
        var federalTaxId: String? = null
        var vendorNumber: String? = null
        var authorizationNumberInvoice: String? = null
        var requestId: String? = null

        for (text in pageTexts) {
            if (federalTaxId == null) {
                // Same-line match (label and value on same line or space-separated)
                val m = Regex("""Federal Tax ID Number:\s*(\d+)""").find(text)
                if (m != null) {
                    federalTaxId = m.groupValues[1]
                } else {
                    // Cross-line fallback: label on one line, value on the next
                    federalTaxId = extractCrossLineValue(text, "Federal Tax ID Number:")
                }
            }

            if (vendorNumber == null) {
                val m = Regex("""Vendor\s+Number:\s*(\S+)""").find(text)
                if (m != null) {
                    vendorNumber = m.groupValues[1]
                } else {
                    vendorNumber = extractCrossLineValue(text, "Vendor Number:")
                }
            }

            if (authorizationNumberInvoice == null) {
                val m = Regex("""Authorization\s+Number:\s*(\S+)""").find(text)
                if (m != null) {
                    authorizationNumberInvoice = m.groupValues[1]
                } else {
                    authorizationNumberInvoice = extractCrossLineValue(text, "Authorization Number:")
                }
            }

            if (requestId == null) {
                // RQID may have no space, optional space, or newline between : and value
                val m = Regex("""RQID\s*:\s*(\S+)""").find(text)
                if (m != null) {
                    requestId = m.groupValues[1]
                } else {
                    requestId = extractCrossLineValue(text, "RQID:")
                }
            }
        }

        return InvoiceFields(
            federalTaxId = federalTaxId,
            vendorNumber = vendorNumber,
            authorizationNumberInvoice = authorizationNumberInvoice,
            requestId = requestId,
        )
    }

    /**
     * Extract a value that appears on the line following the given label.
     *
     * When PDFBox produces a label and its value as separate text blocks at different
     * Y coordinates, the reconstructed page text has them on separate lines. This
     * method finds the label in the text and captures the first non-empty token on
     * the following line.
     *
     * @param text the full page text (may contain newlines)
     * @param label the label to search for (e.g., "Vendor Number:")
     * @return the first token on the line following the label, or null if not found
     */
    internal fun extractCrossLineValue(text: String, label: String): String? {
        val lines = text.split('\n')
        for (i in 0 until lines.size - 1) {
            if (label in lines[i]) {
                // Check if value is on the same line after the label
                val afterLabel = lines[i].substringAfter(label).trim()
                if (afterLabel.isNotEmpty()) {
                    return afterLabel.split(Regex("\\s+")).first()
                }
                // Value is on the next line
                val nextLine = lines[i + 1].trim()
                if (nextLine.isNotEmpty()) {
                    return nextLine.split(Regex("\\s+")).first()
                }
            }
        }
        // Check the last line for same-line match
        if (lines.isNotEmpty() && label in lines.last()) {
            val afterLabel = lines.last().substringAfter(label).trim()
            if (afterLabel.isNotEmpty()) {
                return afterLabel.split(Regex("\\s+")).first()
            }
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Phone extraction (CELL # pattern)
    // -----------------------------------------------------------------------

    /**
     * Extract phone number from CELL # pattern found in text.
     */
    internal fun extractPhone(pageTexts: List<String>): String? {
        val phoneRegex = Regex("""CELL\s*#\s*(\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4})""")
        for (text in pageTexts) {
            val match = phoneRegex.find(text)
            if (match != null) return match.groupValues[1].trim()
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Fallback individual field extraction
    // -----------------------------------------------------------------------

    /**
     * Extract individual fields from concatenated all-pages text as a fallback.
     *
     * This runs after the primary extractors and only fills fields that were
     * not found by their primary source. Handles cases where PDFBox splits
     * labels across text blocks (e.g., "Case" and "ID:" on different lines).
     */
    internal fun extractFallbackFields(
        allText: String,
        headerFields: HeaderFields,
        invoiceFields: InvoiceFields,
    ): FallbackFields {
        var caseId: String? = null
        var requestId: String? = null

        // B3: Case ID fallback — handle "Case" and "ID:" potentially split
        if (headerFields.caseId == null) {
            val collapsed = allText.replace('\n', ' ')
            val m = Regex("""Case\s+ID:\s*(\S+)""").find(collapsed)
            if (m != null) caseId = m.groupValues[1]
        }

        // B4: RQID fallback — search across all pages with cross-line support
        if (invoiceFields.requestId == null) {
            val collapsed = allText.replace('\n', ' ')
            val m = Regex("""RQID\s*:\s*(\S+)""").find(collapsed)
            if (m != null) requestId = m.groupValues[1]
        }

        return FallbackFields(
            caseId = caseId,
            requestId = requestId,
        )
    }

    // -----------------------------------------------------------------------
    // Field merging
    // -----------------------------------------------------------------------

    /**
     * Merge fields from all extraction sources with priority:
     * header > table > invoice > fallback (phone).
     *
     * Higher-priority sources override lower-priority ones when both have a value.
     */
    private fun mergeFields(
        invoiceFields: InvoiceFields,
        tableFields: TableFields,
        headerFields: HeaderFields,
        caseFields: CaseFields,
        phoneFromCell: String?,
        fallbackFields: FallbackFields = FallbackFields(),
    ): ReferralFields {
        // Start with invoice (lowest priority for overlapping fields)
        var firstName: ParsedField<String> = ParsedField.notFound()
        var middleName: ParsedField<String> = ParsedField.notFound()
        var lastName: ParsedField<String> = ParsedField.notFound()
        var caseId: ParsedField<String> = ParsedField.notFound()
        var authorizationNumber: ParsedField<String> = ParsedField.notFound()
        var requestId: ParsedField<String> = ParsedField.notFound()
        var dateOfIssue: ParsedField<String> = ParsedField.notFound()
        var dob: ParsedField<String> = ParsedField.notFound()
        var applicantName: ParsedField<String> = ParsedField.notFound()
        var appointmentDate: ParsedField<String> = ParsedField.notFound()
        var appointmentTime: ParsedField<String> = ParsedField.notFound()
        var streetAddress: ParsedField<String> = ParsedField.notFound()
        var city: ParsedField<String> = ParsedField.notFound()
        var state: ParsedField<String> = ParsedField.notFound()
        var zipCode: ParsedField<String> = ParsedField.notFound()
        var phone: ParsedField<String> = ParsedField.notFound()
        var services: List<ServiceLine> = emptyList()
        var servicesConfidence: Confidence? = null
        var federalTaxId: ParsedField<String> = ParsedField.notFound()
        var vendorNumber: ParsedField<String> = ParsedField.notFound()

        // Fallback fields (LOW priority — last resort individual pattern matching)
        fallbackFields.caseId?.let { caseId = ParsedField.low(it) }
        fallbackFields.requestId?.let { requestId = ParsedField.low(it) }

        // Invoice fields (LOW-MEDIUM priority for overlap fields, MEDIUM for invoice-specific)
        invoiceFields.federalTaxId?.let { federalTaxId = ParsedField.medium(it) }
        invoiceFields.vendorNumber?.let { vendorNumber = ParsedField.medium(it) }
        invoiceFields.authorizationNumberInvoice?.let {
            authorizationNumber = ParsedField.medium(it)
        }
        invoiceFields.requestId?.let { requestId = ParsedField.medium(it) }

        // Table fields (MEDIUM priority — structured but from table extraction)
        tableFields.streetAddress?.let { streetAddress = ParsedField.medium(it) }
        tableFields.city?.let { city = ParsedField.medium(it) }
        tableFields.state?.let { state = ParsedField.medium(it) }
        tableFields.zipCode?.let { zipCode = ParsedField.medium(it) }
        tableFields.phone?.let { phone = ParsedField.medium(it) }
        tableFields.appointmentDate?.let { appointmentDate = ParsedField.medium(it) }
        tableFields.appointmentTime?.let { appointmentTime = ParsedField.medium(it) }
        if (tableFields.services.isNotEmpty()) {
            services = tableFields.services
            servicesConfidence = tableFields.servicesConfidence
        }

        // Header fields (HIGH priority — most reliable source)
        headerFields.dateOfIssue?.let { dateOfIssue = ParsedField.high(it) }
        headerFields.caseId?.let { caseId = ParsedField.high(it) }
        headerFields.firstName?.let { firstName = ParsedField.high(it) }
        headerFields.middleName?.let { middleName = ParsedField.high(it) }
        headerFields.lastName?.let { lastName = ParsedField.high(it) }
        headerFields.dob?.let { dob = ParsedField.high(it) }
        headerFields.applicantName?.let { applicantName = ParsedField.high(it) }
        headerFields.authorizationNumber?.let { authorizationNumber = ParsedField.high(it) }

        // Case number components (HIGH — specific footer pattern)
        val caseNumberFullFooter = caseFields.caseNumberFullFooter?.let {
            ParsedField.high(it)
        } ?: ParsedField.notFound()
        val assignedCode = caseFields.assignedCode?.let {
            ParsedField.high(it)
        } ?: ParsedField.notFound()
        val dccNumber = caseFields.dccNumber?.let {
            ParsedField.high(it)
        } ?: ParsedField.notFound()

        // Fallback: CELL # phone (LOW priority — less structured)
        if (!phone.isPresent && phoneFromCell != null) {
            phone = ParsedField.low(phoneFromCell)
        }

        return ReferralFields(
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            caseId = caseId,
            authorizationNumber = authorizationNumber,
            requestId = requestId,
            dateOfIssue = dateOfIssue,
            dob = dob,
            applicantName = applicantName,
            appointmentDate = appointmentDate,
            appointmentTime = appointmentTime,
            streetAddress = streetAddress,
            city = city,
            state = state,
            zipCode = zipCode,
            phone = phone,
            services = services,
            servicesConfidence = servicesConfidence,
            federalTaxId = federalTaxId,
            vendorNumber = vendorNumber,
            caseNumberFullFooter = caseNumberFullFooter,
            assignedCode = assignedCode,
            dccNumber = dccNumber,
        )
    }

    // -----------------------------------------------------------------------
    // Internal data holders for intermediate extraction results
    // -----------------------------------------------------------------------

    internal data class HeaderFields(
        val dateOfIssue: String? = null,
        val caseId: String? = null,
        val firstName: String? = null,
        val middleName: String? = null,
        val lastName: String? = null,
        val dob: String? = null,
        val applicantName: String? = null,
        val authorizationNumber: String? = null,
    )

    internal data class CaseFields(
        val caseNumberFullFooter: String? = null,
        val assignedCode: String? = null,
        val dccNumber: String? = null,
    )

    internal data class TableFields(
        val streetAddress: String? = null,
        val city: String? = null,
        val state: String? = null,
        val zipCode: String? = null,
        val phone: String? = null,
        val appointmentDate: String? = null,
        val appointmentTime: String? = null,
        val services: List<ServiceLine> = emptyList(),
        val servicesConfidence: Confidence? = null,
    ) {
        fun mergeClaimant(info: ClaimantInfo): TableFields = copy(
            streetAddress = info.streetAddress ?: streetAddress,
            city = info.city ?: city,
            state = info.state ?: state,
            zipCode = info.zipCode ?: zipCode,
            phone = info.phone ?: phone,
        )

        fun mergeAppointment(info: AppointmentInfo): TableFields = copy(
            appointmentDate = info.appointmentDate ?: appointmentDate,
            appointmentTime = info.appointmentTime ?: appointmentTime,
        )
    }

    internal data class InvoiceFields(
        val federalTaxId: String? = null,
        val vendorNumber: String? = null,
        val authorizationNumberInvoice: String? = null,
        val requestId: String? = null,
    )

    internal data class ClaimantInfo(
        val nameFromTable: String? = null,
        val streetAddress: String? = null,
        val city: String? = null,
        val state: String? = null,
        val zipCode: String? = null,
        val phone: String? = null,
    )

    internal data class AppointmentInfo(
        val appointmentDate: String? = null,
        val appointmentTime: String? = null,
    )

    internal data class FallbackFields(
        val caseId: String? = null,
        val requestId: String? = null,
    )
}
