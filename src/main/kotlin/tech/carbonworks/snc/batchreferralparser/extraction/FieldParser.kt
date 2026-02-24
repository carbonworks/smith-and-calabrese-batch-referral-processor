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
 */
class FieldParser {

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
        val pageTexts = textResult.pages.map { page ->
            page.textBlocks.joinToString(" ") { it.text }
        }

        // Extract from each source
        val headerFields = extractHeaderBlock(pageTexts)
        val caseFields = extractCaseNumberComponents(pageTexts)
        val tableFields = extractTableFields(tables)
        val invoiceFields = extractInvoiceFields(pageTexts)
        val phoneFromCell = extractPhone(pageTexts)

        // Merge with priority: header > table > invoice > fallback
        // Start with lowest priority and override with higher priority sources
        return mergeFields(invoiceFields, tableFields, headerFields, caseFields, phoneFromCell)
    }

    // -----------------------------------------------------------------------
    // Header block extraction
    // -----------------------------------------------------------------------

    /**
     * Extract fields from the appointment authorization header block.
     *
     * Looks for the pattern:
     *   Date: ... Case ID: ... RE: ... DOB: ... Applicant: ... Authorization #: ...
     */
    internal fun extractHeaderBlock(pageTexts: List<String>): HeaderFields {
        val headerRegex = Regex(
            """Date:\s*(.+?)\s*Case ID:\s*(.+?)\s*RE:\s*(.+?)\s*DOB:\s*(.+?)\s*Applicant:\s*(.+?)\s*Authorization #:\s*(\S+)"""
        )

        for (text in pageTexts) {
            if ("Case ID:" !in text || "Authorization #:" !in text) continue

            val match = headerRegex.find(text) ?: continue

            val dateOfIssue = match.groupValues[1].trim()
            val caseId = match.groupValues[2].trim()
            val reName = match.groupValues[3].trim()
            val dob = match.groupValues[4].trim()
            val applicantName = match.groupValues[5].trim()
            val authorizationNumber = match.groupValues[6].trim()

            // Parse RE: field into name components
            val nameParts = reName.split(Regex("\\s+"))
            val firstName: String?
            val middleName: String?
            val lastName: String?

            when {
                nameParts.size >= 3 -> {
                    firstName = nameParts[0]
                    middleName = nameParts.subList(1, nameParts.size - 1).joinToString(" ")
                    lastName = nameParts.last()
                }
                nameParts.size == 2 -> {
                    firstName = nameParts[0]
                    middleName = null
                    lastName = nameParts[1]
                }
                else -> {
                    firstName = reName
                    middleName = null
                    lastName = null
                }
            }

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

        return HeaderFields()
    }

    // -----------------------------------------------------------------------
    // Case number components (footer pattern)
    // -----------------------------------------------------------------------

    /**
     * Extract case number components from footer text.
     *
     * Looks for: CASE-NUMBER/ Assigned NNNN null/ DCPS / DCC-NUMBER
     */
    internal fun extractCaseNumberComponents(pageTexts: List<String>): CaseFields {
        val footerRegex = Regex("""^(\S+)/\s*Assigned\s+(\d+)\s+null/\s*DCPS\s*/\s*(\S+)""")

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
     */
    internal fun extractInvoiceFields(pageTexts: List<String>): InvoiceFields {
        var federalTaxId: String? = null
        var vendorNumber: String? = null
        var authorizationNumberInvoice: String? = null
        var requestId: String? = null

        for (text in pageTexts) {
            if (federalTaxId == null) {
                val m = Regex("""Federal Tax ID Number:\s*(\d+)""").find(text)
                if (m != null) federalTaxId = m.groupValues[1]
            }

            if (vendorNumber == null) {
                val m = Regex("""Vendor Number:\s*(\S+)""").find(text)
                if (m != null) vendorNumber = m.groupValues[1]
            }

            if (authorizationNumberInvoice == null) {
                val m = Regex("""Authorization Number:\s*(\S+)""").find(text)
                if (m != null) authorizationNumberInvoice = m.groupValues[1]
            }

            if (requestId == null) {
                val m = Regex("""RQID:(\S+)""").find(text)
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
}
