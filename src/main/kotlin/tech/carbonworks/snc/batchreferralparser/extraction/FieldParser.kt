package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Extracts structured referral fields from raw PDF extraction results.
 *
 * Ports the Python prototype logic from `extract_referral_fields.py` to Kotlin.
 * Takes [ExtractionResult.Success] (word-level text blocks) and [ExtractedTable] data,
 * reconstructs page-level text strings, applies regex patterns and heuristics to
 * extract each target field, and returns a populated [ReferralFields].
 *
 * Merge priority: header > table > invoice > fallback (phone).
 *
 * @param lineYTolerance vertical tolerance in PDF user-space units for reconstructing
 *        lines from text blocks. Wider values merge more aggressively, which helps
 *        when PDFBox extracts header labels and values at slightly different Y offsets.
 *        Default is 5.0f (widened from the original 2.0f to handle real-world PDFs).
 */
class FieldParser(
    private val lineYTolerance: Float = 5.0f,
) {

    /**
     * Parse all referral fields from the given extraction result and tables.
     *
     * @param textResult successful PDF text extraction containing word-level text blocks
     * @param tables structured table data extracted from the PDF
     * @return populated [ReferralFields] with extracted field values
     */
    fun parse(
        textResult: ExtractionResult.Success,
        tables: List<ExtractedTable> = emptyList(),
    ): ReferralFields {
        // Reconstruct page-level text strings from word-level TextBlocks
        val pageTexts = reconstructPageTexts(textResult)

        // Extract from each source
        val headerFields = extractHeaderBlock(pageTexts)
        val caseFields = extractCaseNumberComponents(pageTexts)
        val tableFields = extractTableFields(tables)
        val invoiceFields = extractInvoiceFields(pageTexts)
        val phoneFromCell = extractPhone(pageTexts)

        // Diagnostic: which extraction stages found data (no PHI values)
        val headerHit = headerFields.caseId != null
        val caseHit = caseFields.caseNumberFullFooter != null
        val tableHit = tableFields.city != null || tableFields.services.isNotEmpty()
        val invoiceHit = invoiceFields.federalTaxId != null || invoiceFields.requestId != null
        val phoneHit = phoneFromCell != null
        println("[FieldParser] Source hits — header: $headerHit, case-footer: $caseHit, table: $tableHit, invoice: $invoiceHit, cell-phone: $phoneHit")

        if (!headerHit) {
            val hasCaseIdLabel = pageTexts.any { "Case ID:" in it }
            val hasAuthLabel = pageTexts.any { "Authorization #:" in it }
            val hasDateLabel = pageTexts.any { "Date:" in it }
            val hasRELabel = pageTexts.any { "RE:" in it }
            println("[FieldParser]   Header miss detail — 'Case ID:' present: $hasCaseIdLabel, 'Authorization #:' present: $hasAuthLabel, 'Date:' present: $hasDateLabel, 'RE:' present: $hasRELabel")
        }
        if (!caseHit) {
            val hasAssigned = pageTexts.any { "Assigned" in it }
            val hasDCPS = pageTexts.any { "DCPS" in it }
            println("[FieldParser]   Footer miss detail — 'Assigned' present: $hasAssigned, 'DCPS' present: $hasDCPS")
        }
        if (!invoiceHit) {
            val hasFedTax = pageTexts.any { "Federal Tax ID" in it }
            val hasVendor = pageTexts.any { "Vendor Number" in it }
            val hasRQID = pageTexts.any { "RQID" in it }
            println("[FieldParser]   Invoice miss detail — 'Federal Tax ID' present: $hasFedTax, 'Vendor Number' present: $hasVendor, 'RQID' present: $hasRQID")
        }

        // Merge with priority: header > table > invoice > fallback
        // Start with lowest priority and override with higher priority sources
        return mergeFields(invoiceFields, tableFields, headerFields, caseFields, phoneFromCell)
    }

    /**
     * Reconstruct page-level text strings from word-level TextBlocks.
     *
     * Groups text blocks by Y coordinate (within [lineYTolerance]), sorts each line
     * left-to-right, and joins with spaces. Lines are separated by newlines.
     * This produces much better text reconstruction than a flat join, especially
     * when header labels and values sit at slightly different vertical offsets.
     */
    fun reconstructPageTexts(
        textResult: ExtractionResult.Success,
    ): List<String> {
        return textResult.pages.map { page ->
            reconstructPageText(page.textBlocks)
        }
    }

    /**
     * Reconstruct a single page's text from its text blocks using line-aware grouping.
     */
    private fun reconstructPageText(textBlocks: List<TextBlock>): String {
        if (textBlocks.isEmpty()) return ""

        // Group blocks into lines by Y coordinate
        val lines = mutableListOf<MutableList<TextBlock>>()
        val sortedByY = textBlocks.sortedWith(compareBy({ it.boundingBox.y }, { it.boundingBox.x }))

        var currentLine = mutableListOf(sortedByY[0])
        var currentY = sortedByY[0].boundingBox.y

        for (i in 1 until sortedByY.size) {
            val block = sortedByY[i]
            if (Math.abs(block.boundingBox.y - currentY) < lineYTolerance) {
                currentLine.add(block)
            } else {
                lines.add(currentLine)
                currentLine = mutableListOf(block)
                currentY = block.boundingBox.y
            }
        }
        lines.add(currentLine)

        // Sort each line left-to-right and join
        return lines.joinToString("\n") { line ->
            line.sortedBy { it.boundingBox.x }.joinToString(" ") { it.text }
        }
    }

    // -----------------------------------------------------------------------
    // Header block extraction
    // -----------------------------------------------------------------------

    /**
     * Extract fields from the appointment authorization header block.
     *
     * First tries a combined regex across the full text (with DOT_MATCHES_ALL to
     * handle multi-line headers). If that fails, falls back to extracting each
     * field independently using individual regexes.
     *
     * Also searches across ALL pages and tries concatenating all page texts, so
     * headers split across pages can still be matched.
     */
    internal fun extractHeaderBlock(pageTexts: List<String>): HeaderFields {
        // Combined regex — works across newlines via DOT_MATCHES_ALL
        val headerRegex = Regex(
            """Date:\s*(.+?)\s*Case ID:\s*(.+?)\s*RE:\s*(.+?)\s*DOB:\s*(.+?)\s*Applicant:\s*(.+?)\s*Authorization #:\s*(\S+)""",
            RegexOption.DOT_MATCHES_ALL,
        )

        // Try each page individually
        for (text in pageTexts) {
            val match = headerRegex.find(text)
            if (match != null) {
                return buildHeaderFromCombinedMatch(match)
            }
        }

        // Try concatenating all pages (header split across pages)
        if (pageTexts.size > 1) {
            val allText = pageTexts.joinToString("\n")
            val match = headerRegex.find(allText)
            if (match != null) {
                return buildHeaderFromCombinedMatch(match)
            }
        }

        // Fallback: extract each field independently across all pages
        return extractHeaderFieldsIndividually(pageTexts)
    }

    /**
     * Build [HeaderFields] from a successful combined regex match.
     */
    private fun buildHeaderFromCombinedMatch(match: MatchResult): HeaderFields {
        val dateOfIssue = match.groupValues[1].trim()
        val caseId = match.groupValues[2].trim()
        val reName = match.groupValues[3].trim()
        val dob = match.groupValues[4].trim()
        val applicantName = match.groupValues[5].trim()
        val authorizationNumber = match.groupValues[6].trim()

        val (firstName, middleName, lastName) = parseNameParts(reName)

        return HeaderFields(
            dateOfIssue = dateOfIssue,
            caseId = caseId,
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            dob = dob,
            applicantName = applicantName,
            authorizationNumber = authorizationNumber,
        )
    }

    /**
     * Extract header fields individually when the combined regex fails.
     *
     * Each field is searched independently across all pages. This handles PDFs
     * where only some fields are present, or fields appear on different pages.
     */
    private fun extractHeaderFieldsIndividually(pageTexts: List<String>): HeaderFields {
        val allText = pageTexts.joinToString("\n")

        val dateRegex = Regex("""Date:\s*(\S+)""")
        val caseIdRegex = Regex("""Case ID:\s*(\S+)""")
        val reRegex = Regex("""RE:\s*(.+?)(?:\s*DOB:|\s*Applicant:|\s*Authorization\s*#:|\s*$)""",
            RegexOption.DOT_MATCHES_ALL)
        val dobRegex = Regex("""DOB:\s*(\S+)""")
        val applicantRegex = Regex("""Applicant:\s*(.+?)(?:\s*Authorization\s*#:|\s*$)""",
            RegexOption.DOT_MATCHES_ALL)
        val authRegex = Regex("""Authorization\s*#:\s*(\S+)""")

        val dateOfIssue = dateRegex.find(allText)?.groupValues?.get(1)?.trim()
        val caseId = caseIdRegex.find(allText)?.groupValues?.get(1)?.trim()
        val dob = dobRegex.find(allText)?.groupValues?.get(1)?.trim()
        val authorizationNumber = authRegex.find(allText)?.groupValues?.get(1)?.trim()

        val reName = reRegex.find(allText)?.groupValues?.get(1)?.trim()
        val applicantName = applicantRegex.find(allText)?.groupValues?.get(1)?.trim()

        var firstName: String? = null
        var middleName: String? = null
        var lastName: String? = null
        if (reName != null) {
            val parts = parseNameParts(reName)
            firstName = parts.first
            middleName = parts.second
            lastName = parts.third
        }

        // Only return fields if we found at least one
        return if (listOfNotNull(dateOfIssue, caseId, dob, authorizationNumber, reName, applicantName).isEmpty()) {
            HeaderFields()
        } else {
            HeaderFields(
                dateOfIssue = dateOfIssue,
                caseId = caseId,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                dob = dob,
                applicantName = applicantName,
                authorizationNumber = authorizationNumber,
            )
        }
    }

    /**
     * Parse a full name string into (first, middle, last) components.
     */
    private fun parseNameParts(reName: String): Triple<String?, String?, String?> {
        // Collapse any internal whitespace (newlines, multiple spaces)
        val cleanName = reName.replace(Regex("""\s+"""), " ").trim()
        val nameParts = cleanName.split(" ")

        return when {
            nameParts.size >= 3 -> Triple(
                nameParts[0],
                nameParts.subList(1, nameParts.size - 1).joinToString(" "),
                nameParts.last(),
            )
            nameParts.size == 2 -> Triple(nameParts[0], null, nameParts[1])
            else -> Triple(cleanName, null, null)
        }
    }

    // -----------------------------------------------------------------------
    // Case number components (footer pattern)
    // -----------------------------------------------------------------------

    /**
     * Extract case number components from footer text.
     *
     * Looks for: CASE-NUMBER/ Assigned NNNN [null/] [DCPS|agency] / DCC-NUMBER
     *
     * The regex is flexible: allows varied whitespace (including newlines),
     * makes `null/` optional, and makes the agency code (DCPS) optional to
     * handle different PDF formats.
     */
    internal fun extractCaseNumberComponents(pageTexts: List<String>): CaseFields {
        // Flexible footer regex:
        // - \s+ between components (matches spaces and newlines)
        // - (?:null/\s*)? makes "null/" optional
        // - (?:\S+\s*/\s*)? makes the agency code (DCPS) optional
        val footerRegex = Regex(
            """(\S+)/\s*Assigned\s+(\d+)\s+(?:null/\s*)?(?:\S+\s*/\s*)?(\S+)""",
            RegexOption.MULTILINE,
        )

        // Search each page individually
        for (text in pageTexts) {
            val match = footerRegex.find(text)
            if (match != null) {
                return CaseFields(
                    caseNumberFullFooter = match.groupValues[1].trim(),
                    assignedCode = match.groupValues[2].trim(),
                    dccNumber = match.groupValues[3].trim(),
                )
            }
        }

        // Try across all pages concatenated
        if (pageTexts.size > 1) {
            val allText = pageTexts.joinToString("\n")
            val match = footerRegex.find(allText)
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
                            result = result.copy(services = services)
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
     * Looks for: Federal Tax ID Number, Vendor Number, Authorization Number,
     * RQID, Pay to, On/At schedule.
     *
     * All patterns are case-insensitive and support alternate label variations
     * (e.g., "Federal Tax ID" vs "Federal Tax ID Number:", "Fed Tax ID:").
     * Whitespace between label and value is flexible (including newlines).
     */
    internal fun extractInvoiceFields(pageTexts: List<String>): InvoiceFields {
        var federalTaxId: String? = null
        var vendorNumber: String? = null
        var authorizationNumberInvoice: String? = null
        var requestId: String? = null

        // Search each page, then try concatenated text
        val textsToSearch = if (pageTexts.size > 1) {
            pageTexts + listOf(pageTexts.joinToString("\n"))
        } else {
            pageTexts
        }

        for (text in textsToSearch) {
            if (federalTaxId == null) {
                // Match: "Federal Tax ID Number:", "Federal Tax ID:", "Fed Tax ID:", etc.
                val m = Regex(
                    """(?:Federal\s+Tax\s+ID|Fed\.?\s+Tax\s+ID)(?:\s+Number)?\s*:?\s*(\d+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) federalTaxId = m.groupValues[1]
            }

            if (vendorNumber == null) {
                val m = Regex(
                    """Vendor\s+Number\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) vendorNumber = m.groupValues[1]
            }

            if (authorizationNumberInvoice == null) {
                val m = Regex(
                    """Authorization\s+Number\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) authorizationNumberInvoice = m.groupValues[1]
            }

            if (requestId == null) {
                // Match "RQID:RQ-42" or "RQID: RQ-42" (with or without space)
                val m = Regex(
                    """RQID\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) requestId = m.groupValues[1]
            }
        }

        return InvoiceFields(
            federalTaxId = federalTaxId,
            vendorNumber = vendorNumber,
            authorizationNumberInvoice = authorizationNumberInvoice,
            requestId = requestId,
        )
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
    ): ReferralFields {
        // Start with all fields null (lowest priority)
        var firstName: String? = null
        var middleName: String? = null
        var lastName: String? = null
        var caseId: String? = null
        var authorizationNumber: String? = null
        var requestId: String? = null
        var dateOfIssue: String? = null
        var dob: String? = null
        var applicantName: String? = null
        var appointmentDate: String? = null
        var appointmentTime: String? = null
        var streetAddress: String? = null
        var city: String? = null
        var state: String? = null
        var zipCode: String? = null
        var phone: String? = null
        var services: List<ServiceLine> = emptyList()
        var federalTaxId: String? = null
        var vendorNumber: String? = null

        // Invoice fields (lowest priority for overlapping fields)
        invoiceFields.federalTaxId?.let { federalTaxId = it }
        invoiceFields.vendorNumber?.let { vendorNumber = it }
        invoiceFields.authorizationNumberInvoice?.let { authorizationNumber = it }
        invoiceFields.requestId?.let { requestId = it }

        // Table fields (medium priority — structured but from table extraction)
        tableFields.streetAddress?.let { streetAddress = it }
        tableFields.city?.let { city = it }
        tableFields.state?.let { state = it }
        tableFields.zipCode?.let { zipCode = it }
        tableFields.phone?.let { phone = it }
        tableFields.appointmentDate?.let { appointmentDate = it }
        tableFields.appointmentTime?.let { appointmentTime = it }
        if (tableFields.services.isNotEmpty()) {
            services = tableFields.services
        }

        // Header fields (highest priority — most reliable source)
        headerFields.dateOfIssue?.let { dateOfIssue = it }
        headerFields.caseId?.let { caseId = it }
        headerFields.firstName?.let { firstName = it }
        headerFields.middleName?.let { middleName = it }
        headerFields.lastName?.let { lastName = it }
        headerFields.dob?.let { dob = it }
        headerFields.applicantName?.let { applicantName = it }
        headerFields.authorizationNumber?.let { authorizationNumber = it }

        // Fallback: CELL # phone (lowest priority — less structured)
        if (phone == null && phoneFromCell != null) {
            phone = phoneFromCell
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
            federalTaxId = federalTaxId,
            vendorNumber = vendorNumber,
            caseNumberFullFooter = caseFields.caseNumberFullFooter,
            assignedCode = caseFields.assignedCode,
            dccNumber = caseFields.dccNumber,
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

    // -----------------------------------------------------------------------
    // Diagnostic utilities
    // -----------------------------------------------------------------------

    companion object {
        /**
         * Produce a sanitized diagnostic text dump from an extraction result.
         *
         * Shows page number, line count, and which field labels were found on each page.
         * Does NOT include actual field values (PHI constraint).
         *
         * Example output:
         *   [Page 1] 15 lines -- labels found: Date, Case ID, RE, DOB, Authorization #
         *   [Page 2] 8 lines -- labels found: (none)
         *   [Page 3] 12 lines -- labels found: Federal Tax ID Number, Vendor Number, RQID
         *
         * @param textResult the successful extraction result to diagnose
         * @param lineYTolerance tolerance for reconstructing lines (default 5.0f)
         * @return multi-line diagnostic string (safe to log — contains no PHI)
         */
        fun dumpPageTexts(
            textResult: ExtractionResult.Success,
            lineYTolerance: Float = 5.0f,
        ): String {
            val parser = FieldParser(lineYTolerance)
            val pageTexts = parser.reconstructPageTexts(textResult)

            val knownLabels = listOf(
                "Date:", "Case ID:", "RE:", "DOB:", "Applicant:",
                "Authorization #:", "Authorization Number:",
                "Federal Tax ID Number:", "Federal Tax ID:",
                "Fed Tax ID:", "Vendor Number:", "RQID:",
                "Claimant Information", "Date and Time",
                "Services Authorized", "Code:", "CELL #",
                "Assigned",
            )

            return pageTexts.mapIndexed { index, text ->
                val lineCount = if (text.isBlank()) 0 else text.lines().size
                val foundLabels = knownLabels.filter { label -> label in text }
                val labelsStr = if (foundLabels.isEmpty()) "(none)" else foundLabels.joinToString(", ")
                "[Page ${index + 1}] $lineCount lines -- labels found: $labelsStr"
            }.joinToString("\n")
        }
    }
}
