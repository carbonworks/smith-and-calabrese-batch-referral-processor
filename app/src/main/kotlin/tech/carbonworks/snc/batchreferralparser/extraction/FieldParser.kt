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

    companion object {
        /** Y-coordinate tolerance for grouping text blocks on the same line. */
        private const val LINE_Y_TOLERANCE = 3f

        /**
         * Expected fields that should normally be present in a valid referral.
         * Each entry maps a human-readable field name to an accessor on [ReferralFields].
         * Fields not in this list (e.g., middleName, applicantName, assignedCode)
         * are considered optional and will not trigger missing-field warnings.
         */
        internal val EXPECTED_FIELDS: List<Pair<String, (ReferralFields) -> String?>> = listOf(
            "Claimant first name" to { it.firstName },
            "Claimant last name" to { it.lastName },
            "Date of birth" to { it.dob },
            "Case ID" to { it.caseId },
            "Date of issue" to { it.dateOfIssue },
            "Authorization number" to { it.authorizationNumber },
            "Appointment date" to { it.appointmentDate },
            "Street address" to { it.streetAddress },
            "City" to { it.city },
            "State" to { it.state },
            "ZIP code" to { it.zipCode },
        )

        /**
         * Check [fields] for expected fields that are null or blank and return
         * a [ParsingWarning] for each missing one.
         *
         * These warnings use the stage "completeness" to distinguish them from
         * the stage-specific extraction warnings (header, table, invoice, footer).
         */
        fun generateMissingFieldWarnings(fields: ReferralFields): List<ParsingWarning> {
            return EXPECTED_FIELDS.mapNotNull { (name, accessor) ->
                val value = accessor(fields)
                if (value.isNullOrBlank()) {
                    ParsingWarning(
                        field = name,
                        stage = "completeness",
                        message = "$name not found",
                    )
                } else {
                    null
                }
            }
        }

    }

    /**
     * Parse all referral fields from the given extraction result and tables.
     *
     * @param textResult successful PDF text extraction containing word-level text blocks
     * @param tables structured table data extracted from the PDF
     * @return [ParseResult] containing populated [ReferralFields] and any extraction warnings
     */
    fun parse(
        textResult: ExtractionResult.Success,
        tables: List<ExtractedTable> = emptyList(),
    ): ParseResult {
        // Reconstruct page-level text strings from word-level TextBlocks
        val pageTexts = reconstructPageTexts(textResult)
        val warnings = mutableListOf<ParsingWarning>()
        println("[Parser] Parsing: ${pageTexts.size} page(s) reconstructed")

        // Extract from each source
        val headerFields = extractHeaderBlock(pageTexts)
        val headerCount = listOfNotNull(
            headerFields.dateOfIssue, headerFields.caseId, headerFields.firstName,
            headerFields.lastName, headerFields.dob, headerFields.applicantName,
            headerFields.authorizationNumber,
        ).size
        println("[Parser] Header stage: $headerCount field(s) extracted")

        val caseFields = extractCaseNumberComponents(pageTexts)
        val caseCount = listOfNotNull(caseFields.caseNumberFullFooter, caseFields.assignedCode, caseFields.dccNumber).size
        println("[Parser] Footer stage: $caseCount field(s) extracted")

        val tableFields = extractTableFields(tables)
        val tableCount = listOfNotNull(
            tableFields.streetAddress, tableFields.city, tableFields.state,
            tableFields.zipCode, tableFields.phone, tableFields.appointmentDate,
            tableFields.appointmentTime,
        ).size + if (tableFields.services.isNotEmpty()) 1 else 0
        println("[Parser] Table stage: $tableCount field(s) extracted, ${tableFields.services.size} service(s)")

        val invoiceFields = extractInvoiceFields(pageTexts)
        val invoiceCount = listOfNotNull(
            invoiceFields.providerName, invoiceFields.federalTaxId, invoiceFields.vendorNumber,
            invoiceFields.authorizationNumberInvoice, invoiceFields.requestId,
        ).size
        println("[Parser] Invoice stage: $invoiceCount field(s) extracted")

        val postTableFields = extractPostTableFields(pageTexts)
        val postTableCount = listOfNotNull(postTableFields.specialInstructions, postTableFields.examinerNameContact).size
        println("[Parser] Post-table stage: $postTableCount field(s) extracted")

        val phoneFromCell = extractPhone(pageTexts)
        if (phoneFromCell != null) println("[Parser] Phone fallback: found via CELL # pattern")

        // Individual fallback extraction across concatenated all-pages text
        // for fields that may not be found by their primary extractor
        val allText = pageTexts.joinToString("\n")
        val fallbackFields = extractFallbackFields(allText, headerFields, invoiceFields)

        // Collect warnings for header stage
        val headerHit = headerFields.caseId != null
        if (!headerHit) {
            val hasCaseIdLabel = pageTexts.any { "Case ID:" in it }
            val hasAuthLabel = pageTexts.any { "Authorization #:" in it }
            val hasDateLabel = pageTexts.any { "Date:" in it }
            val hasRELabel = pageTexts.any { "RE:" in it }
            if (hasCaseIdLabel) {
                warnings.add(ParsingWarning("Case ID", "header", "Label found but pattern did not match"))
            }
            if (hasAuthLabel && headerFields.authorizationNumber == null) {
                warnings.add(ParsingWarning("Authorization #", "header", "Label found but pattern did not match"))
            }
            if (hasDateLabel && headerFields.dateOfIssue == null) {
                warnings.add(ParsingWarning("Date of Issue", "header", "Label found but pattern did not match"))
            }
            if (hasRELabel && headerFields.firstName == null) {
                warnings.add(ParsingWarning("RE (Name)", "header", "Label found but pattern did not match"))
            }
        }

        // Collect warnings for footer stage
        val caseHit = caseFields.caseNumberFullFooter != null
        if (!caseHit) {
            val hasAssigned = pageTexts.any { "Assigned" in it }
            val hasDCPS = pageTexts.any { "DCPS" in it }
            if (hasAssigned || hasDCPS) {
                warnings.add(ParsingWarning("Case Number (Footer)", "footer", "Footer labels found but pattern did not match"))
            }
        }

        // Collect warnings for invoice stage
        val invoiceHit = invoiceFields.federalTaxId != null || invoiceFields.requestId != null
        if (!invoiceHit) {
            val hasFedTax = pageTexts.any { "Federal Tax ID" in it }
            val hasVendor = pageTexts.any { "Vendor Number" in it }
            val hasRQID = pageTexts.any { "RQID" in it }
            if (hasFedTax) {
                warnings.add(ParsingWarning("Federal Tax ID", "invoice", "Label found but pattern did not match"))
            }
            if (hasVendor) {
                warnings.add(ParsingWarning("Vendor Number", "invoice", "Label found but pattern did not match"))
            }
            if (hasRQID) {
                warnings.add(ParsingWarning("Request ID", "invoice", "Label found but pattern did not match"))
            }
        }

        // Collect warnings for table stage
        if (tables.isNotEmpty()) {
            val hasClaimantCell = tables.any { table ->
                table.cells.any { it.content.startsWith("Claimant Information") }
            }
            val hasDateTimeCell = tables.any { table ->
                table.cells.any { "Date and Time" in it.content }
            }
            val hasServicesCell = tables.any { table ->
                table.cells.any { "Services Authorized" in it.content || "Code:" in it.content }
            }
            if (hasClaimantCell && tableFields.city == null && tableFields.phone == null) {
                warnings.add(ParsingWarning("Claimant Info", "table", "Cell found but address/phone pattern did not match"))
            }
            if (hasDateTimeCell && tableFields.appointmentDate == null && tableFields.appointmentTime == null) {
                warnings.add(ParsingWarning("Appointment Date/Time", "table", "Cell found but date/time pattern did not match"))
            }
            if (hasServicesCell && tableFields.services.isEmpty()) {
                warnings.add(ParsingWarning("Services", "table", "Cell found but service code pattern did not match"))
            }
        }

        // Merge with priority: header > table > invoice > fallback
        val fields = mergeFields(invoiceFields, tableFields, headerFields, caseFields, phoneFromCell, fallbackFields, postTableFields)

        // Generate missing-field completeness warnings for expected fields
        val missingWarnings = generateMissingFieldWarnings(fields)
        warnings.addAll(missingWarnings)

        val filled = fields.filledFieldCount()
        println("[Parser] Merge complete: $filled field(s) filled, ${warnings.size} warning(s)")
        if (missingWarnings.isNotEmpty()) {
            val missingNames = missingWarnings.map { it.field }
            println("[Parser] Missing expected fields: ${missingNames.joinToString(", ")}")
        }

        return ParseResult(fields = fields, warnings = warnings)
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
            if (page.strippedText.isNotEmpty()) {
                page.strippedText
            } else {
                reconstructPageText(page.textBlocks)
            }
        }
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
     * First tries a combined regex across the full text. The header regex uses
     * [\s\S] to handle cross-line matches when text blocks are reconstructed with
     * newlines between different Y-coordinate groups.
     *
     * If that fails, falls back to extracting each field independently using
     * individual regexes. Also searches across ALL pages and tries concatenating
     * all page texts, so headers split across pages can still be matched.
     */
    internal fun extractHeaderBlock(pageTexts: List<String>): HeaderFields {
        // Use [\s\S] instead of . so the pattern matches across newlines
        val headerRegex = Regex(
            """Date:[\s\S]*?(\S[\s\S]*?)\s*Case\s+ID:\s*(\S+)[\s\S]*?RE:\s*([\s\S]*?)\s*DOB:\s*([\s\S]*?)\s*Applicant:\s*([\s\S]*?)\s*Authorization\s*#:\s*(\S+)"""
        )

        // Try each page individually
        for (text in pageTexts) {
            if ("Case" !in text || "Authorization" !in text) continue

            // Collapse the page text to a single line for header matching,
            // since the header is logically one block but may span multiple Y-coords
            val collapsed = text.replace('\n', ' ').replace(Regex("\\s+"), " ")

            val match = headerRegex.find(collapsed) ?: continue
            return buildHeaderFromCombinedMatch(match)
        }

        // Try concatenating all pages (header split across pages)
        if (pageTexts.size > 1) {
            val allText = pageTexts.joinToString("\n")
            val collapsed = allText.replace('\n', ' ').replace(Regex("\\s+"), " ")
            val match = headerRegex.find(collapsed)
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
        val rawApplicantName = match.groupValues[5].trim()
        val authorizationNumber = match.groupValues[6].trim()

        val (firstName, middleName, lastName) = parseNameParts(reName)
        // B11 fix: apply CamelCase splitting to applicant name
        val applicantName = splitCamelCaseName(rawApplicantName)

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

        // B9 fix: require a date-like pattern after "Date:" to avoid capturing
        // nearby form instructions like "Do not write in this space".
        // Accepts: "MM/DD/YYYY", "Month DD, YYYY", "Month D, YYYY"
        val dateRegex = Regex("""Date:\s*(\d{1,2}/\d{1,2}/\d{2,4}|(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s*\d{4})""")
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
        // B11 fix: apply CamelCase splitting to applicant name
        val applicantName = applicantRegex.find(allText)?.groupValues?.get(1)?.trim()
            ?.let { splitCamelCaseName(it) }

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
     * Split a CamelCase name string into space-separated words.
     *
     * PDFBox sometimes concatenates adjacent text blocks without spaces, producing
     * names like "JohnSmith" instead of "John Smith". This inserts a space before
     * each uppercase letter that follows a lowercase letter.
     *
     * Examples:
     *   "JaneSmith" -> "Jane Smith"
     *   "JohnMichaelDoe" -> "John Michael Doe"
     *   "ALLCAPS" -> "ALLCAPS" (unchanged — no lowercase-to-uppercase transition)
     *   "Already Spaced" -> "Already Spaced" (unchanged)
     */
    internal fun splitCamelCaseName(name: String): String {
        return name.replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
    }

    /**
     * Parse a full name string into (first, middle, last) components.
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

        // Collapse any internal whitespace (newlines, multiple spaces)
        val cleanName = normalized.replace(Regex("""\s+"""), " ").trim()
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
     * Looks for: CASE-NUMBER/ Assigned NNNN [null/] [AGENCY /] DCC-NUMBER [/ trailing...]
     *
     * The footer may contain trailing /-separated components like OMB numbers that
     * should not be captured. The regex stops the DCC capture at the next / or end of text.
     */
    internal fun extractCaseNumberComponents(pageTexts: List<String>): CaseFields {
        // B10 fix: flexible footer regex that handles spaces around slashes.
        // Real PDF footer text from PDFBox may have varied whitespace around "/" separators.
        // Example: "CASENUMBER/ Assigned 9106 null/ DCPS / REQUESTID / OMB No. 0960-0555 / 98022179"
        // - \s*/\s* or \s*/ between components allows spaces around slashes
        // - (?:null\s*/\s*)? makes "null/" or "null /" optional
        // - (?:[A-Z]+\s*/\s*)? makes the agency code (DCPS /) optional
        // - (\S+) captures the request ID / DCC number before the next " /" or end-of-line
        val footerRegex = Regex(
            """(\S+)\s*/\s*Assigned\s+(\d+)\s+(?:null\s*/\s*)?(?:[A-Z]+\s*/\s*)?(\S+)""",
            RegexOption.MULTILINE,
        )

        // Search each page individually
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
     * Expects newline-separated format (TableExtractor now preserves line breaks
     * from PDF cell structure):
     * ```
     * Claimant Information
     * FIRST MIDDLE LAST
     * 123 STREET ADDRESS
     * CITY, ST 12345
     * 555-123-4567
     * ```
     *
     * If only a single line remains after stripping the prefix (degenerate case),
     * the multi-line parser handles it gracefully by classifying the line by its
     * content pattern.
     */
    internal fun parseClaimantCell(text: String): ClaimantInfo {
        val cleaned = text.removePrefix("Claimant Information").trim()

        // Split on newlines and filter out blanks
        val lines = cleaned.split(Regex("\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return ClaimantInfo()
        }

        return parseClaimantCellMultiLine(lines)
    }

    /**
     * Structural multi-line parser for claimant cell.
     *
     * Expects lines in order: name, street address, city/state/zip, phone.
     * Lines are identified by content patterns rather than strict position,
     * so missing lines are handled gracefully.
     */
    private fun parseClaimantCellMultiLine(lines: List<String>): ClaimantInfo {
        var nameFromTable: String? = null
        var streetAddress: String? = null
        var city: String? = null
        var state: String? = null
        var zipCode: String? = null
        var phone: String? = null

        val phoneRegex = Regex("""\(?\d{3}\)?[-.\s]?\d{3}[-.\s]?\d{4}""")
        val cityStateZipRegex = Regex("""(.+?),\s*([A-Z]{2})\s*(\d{5}(?:-\d{4})?)""")

        // Classify each line by its content pattern
        val unclassified = mutableListOf<String>()

        for (line in lines) {
            when {
                // Phone line: entire line matches a phone pattern
                phone == null && phoneRegex.matches(line.trim()) -> {
                    phone = line.trim()
                }
                // City/state/zip line: contains "CITY, ST 12345" pattern
                city == null && cityStateZipRegex.containsMatchIn(line) -> {
                    val match = cityStateZipRegex.find(line)!!
                    city = match.groupValues[1].trim()
                    state = match.groupValues[2].trim()
                    zipCode = match.groupValues[3].trim()
                }
                else -> {
                    unclassified.add(line)
                }
            }
        }

        // Among unclassified lines, separate name from street address.
        // Heuristic: a street address line starts with a digit.
        if (unclassified.isNotEmpty()) {
            // Find the first line that starts with a digit — that's the street address
            val streetIndex = unclassified.indexOfFirst {
                it.isNotEmpty() && it[0].isDigit()
            }

            if (streetIndex >= 0) {
                // Everything before the street line is the name
                if (streetIndex > 0) {
                    nameFromTable = unclassified.subList(0, streetIndex).joinToString(" ")
                }
                streetAddress = unclassified[streetIndex]
                // If there are additional unclassified lines after street, append them
                // (handles multi-line street addresses like "123 MAIN ST\nAPT 4")
                if (streetIndex + 1 < unclassified.size) {
                    val extra = unclassified.subList(streetIndex + 1, unclassified.size)
                        .joinToString(" ")
                    if (nameFromTable == null) {
                        // Street was first line, remaining could be more address
                        streetAddress = "$streetAddress $extra"
                    }
                }
            } else {
                // No digit-starting line found — treat first line as name
                nameFromTable = unclassified.first()
                if (unclassified.size > 1) {
                    // Remaining unclassified lines become street address
                    streetAddress = unclassified.subList(1, unclassified.size)
                        .joinToString(" ")
                }
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
     *
     * All patterns are case-insensitive and support alternate label variations
     * (e.g., "Federal Tax ID" vs "Federal Tax ID Number:", "Fed Tax ID:").
     */
    internal fun extractInvoiceFields(pageTexts: List<String>): InvoiceFields {
        var providerName: String? = null
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
                if (m != null) {
                    federalTaxId = m.groupValues[1]
                } else {
                    // Cross-line fallback: label on one line, value on the next
                    federalTaxId = extractCrossLineValue(text, "Federal Tax ID Number:")
                }
            }

            if (vendorNumber == null) {
                val m = Regex(
                    """Vendor\s+Number\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) {
                    vendorNumber = m.groupValues[1]
                } else {
                    vendorNumber = extractCrossLineValue(text, "Vendor Number:")
                }
            }

            if (authorizationNumberInvoice == null) {
                val m = Regex(
                    """Authorization\s+Number\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) {
                    authorizationNumberInvoice = m.groupValues[1]
                } else {
                    authorizationNumberInvoice = extractCrossLineValue(text, "Authorization Number:")
                }
            }

            if (requestId == null) {
                // RQID may have no space, optional space, or newline between : and value
                val m = Regex(
                    """RQID\s*:\s*(\S+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) {
                    requestId = m.groupValues[1]
                } else {
                    requestId = extractCrossLineValue(text, "RQID:")
                }
            }

            if (providerName == null) {
                // "Pay to:" followed by the provider name (first line of mailing address).
                // The name may be on the same line or the next line.
                val m = Regex(
                    """Pay\s+to\s*:\s*(.+)""",
                    RegexOption.IGNORE_CASE,
                ).find(text)
                if (m != null) {
                    // Collapse whitespace and take the first line as the provider name
                    val raw = m.groupValues[1].trim()
                    val firstLine = raw.split(Regex("\\r?\\n")).first().trim()
                    if (firstLine.isNotEmpty()) {
                        providerName = firstLine
                    }
                } else {
                    // Cross-line fallback: "Pay to:" on one line, name on the next
                    val crossLine = extractCrossLineValue(text, "Pay to:")
                    if (crossLine != null) {
                        // extractCrossLineValue returns only the first token;
                        // for provider name we want the full line after the label
                        val lines = text.split('\n')
                        for (i in lines.indices) {
                            if ("Pay to:" in lines[i]) {
                                val afterLabel = lines[i].substringAfter("Pay to:").trim()
                                if (afterLabel.isNotEmpty()) {
                                    providerName = afterLabel
                                } else if (i + 1 < lines.size) {
                                    val nextLine = lines[i + 1].trim()
                                    if (nextLine.isNotEmpty()) {
                                        providerName = nextLine
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }
        }

        return InvoiceFields(
            providerName = providerName,
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
    // Post-table field extraction (Special Instructions + Examiner)
    // -----------------------------------------------------------------------

    /**
     * Extract fields that appear after the appointment authorization table:
     * - **Special Instructions**: text between the table and "Thank you for your help."
     * - **Examiner Name and Contact**: text after "Thank you for your help."
     *
     * The "Thank you for your help" marker is used as the boundary between the two
     * fields. If the marker is not found, no fields are extracted. The extraction
     * is flexible about whitespace, punctuation, and line breaks around the marker.
     *
     * @param pageTexts reconstructed page-level text strings
     * @return [PostTableFields] with any extracted values
     */
    internal fun extractPostTableFields(pageTexts: List<String>): PostTableFields {
        // Search across all pages for the "Thank you" marker
        val allText = pageTexts.joinToString("\n")

        // Find the "Thank you for your help" marker (case-insensitive, flexible punctuation)
        val thankYouRegex = Regex("""Thank\s+you\s+for\s+your\s+help\.?""", RegexOption.IGNORE_CASE)
        val thankYouMatch = thankYouRegex.find(allText) ?: return PostTableFields()

        // --- Special Instructions ---
        // Text between common markers above and the "Thank you" line.
        // Look for text between known end-of-table markers and the "Thank you" line.
        // Common markers that precede special instructions:
        //   - "Fee:" followed by dollar amount (end of services table)
        //   - The end of any table cell content
        // We look for text in the region before "Thank you" that doesn't belong to the
        // header, table, or invoice sections. Use a pragmatic approach: capture text
        // from the line after known end-of-table patterns up to the "Thank you" line.
        val specialInstructions = extractSpecialInstructions(allText, thankYouMatch.range.first)

        // --- Examiner Name and Contact ---
        // Everything after "Thank you for your help." up to a reasonable end boundary.
        val examinerNameContact = extractExaminerInfo(allText, thankYouMatch.range.last + 1)

        val result = PostTableFields(
            specialInstructions = specialInstructions?.ifBlank { null },
            examinerNameContact = examinerNameContact?.ifBlank { null },
        )

        return result
    }

    /**
     * Extract special instructions text from the region before the "Thank you" marker.
     *
     * Looks backward from the marker position for instruction-like text. The special
     * instructions section typically appears after the services authorized table and
     * before the "Thank you" line. We search for patterns like:
     * - Text after "Special Instructions:" label
     * - Text between common end-of-table markers (e.g., last "Fee:" line) and "Thank you"
     * - Text between "Eastern" timezone reference and "Thank you"
     *
     * @param allText the concatenated text from all pages
     * @param thankYouStart the character index where "Thank you" begins
     * @return the extracted special instructions text, or null if not found
     */
    private fun extractSpecialInstructions(allText: String, thankYouStart: Int): String? {
        val beforeThankYou = allText.substring(0, thankYouStart)

        // Strategy 1: Explicit "Special Instructions:" label
        val labelRegex = Regex("""Special\s+Instructions?\s*:\s*(.+)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val labelMatch = labelRegex.find(beforeThankYou)
        if (labelMatch != null) {
            val text = labelMatch.groupValues[1].trim()
            if (text.isNotBlank()) return normalizeWhitespace(text)
        }

        // Strategy 2: Capture text between the last known section-end marker and
        // "Thank you". We find the rightmost occurrence of any marker string and
        // take everything after it as the candidate special instructions text.
        //
        // Each marker regex is searched; we pick whichever match ends latest
        // (closest to the "Thank you" marker) to minimize noise.
        val sectionEndMarkers = listOf(
            Regex("""(?:Eastern\s+\w+\s+Time|E[SD]T)""", RegexOption.IGNORE_CASE),
            Regex("Fee:" + "\\s*" + "\\$" + "\\s*" + "[\\d,.]+" ),
            Regex("""Procedure\s+Type\s+Code:\s*\S+"""),
        )

        var latestEnd = -1
        for (marker in sectionEndMarkers) {
            val matches = marker.findAll(beforeThankYou).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                if (lastMatch.range.last > latestEnd) {
                    latestEnd = lastMatch.range.last
                }
            }
        }

        if (latestEnd >= 0) {
            val candidateStart = latestEnd + 1
            if (candidateStart < beforeThankYou.length) {
                val candidate = beforeThankYou.substring(candidateStart).trim()
                if (candidate.isNotBlank()) {
                    return normalizeWhitespace(candidate)
                }
            }
        }

        return null
    }

    /**
     * Extract examiner name and contact information from text after "Thank you for your help."
     *
     * Captures the text block following the marker, up to a reasonable end boundary:
     * - The next major section header (e.g., footer case number line, "CASE-NUMBER/")
     * - A page break or form feed
     * - End of text
     *
     * The captured text typically contains:
     * - Examiner name
     * - Phone number and/or email
     * - Title or organization
     *
     * @param allText the concatenated text from all pages
     * @param afterThankYouStart character index immediately after "Thank you for your help."
     * @return the extracted examiner info text, or null if nothing meaningful follows
     */
    private fun extractExaminerInfo(allText: String, afterThankYouStart: Int): String? {
        if (afterThankYouStart >= allText.length) return null

        val afterText = allText.substring(afterThankYouStart)

        // End boundaries: footer case number pattern, "Assigned" keyword, RQID barcode,
        // "Federal Tax ID", form feed, or page markers
        val endBoundaryRegex = Regex(
            """(?:\S+\s*/\s*Assigned\s+\d+|RQID\s*:|Federal\s+Tax\s+ID|Authorization\s+Number\s*:|Vendor\s+Number\s*:|Pay\s+to\s*:|\f)""",
            RegexOption.IGNORE_CASE,
        )

        val endMatch = endBoundaryRegex.find(afterText)
        val examinerText = if (endMatch != null) {
            afterText.substring(0, endMatch.range.first)
        } else {
            afterText
        }

        val cleaned = normalizeWhitespace(examinerText.trim())
        return if (cleaned.isNotBlank()) cleaned else null
    }

    /**
     * Normalize internal whitespace in extracted text: collapse runs of whitespace
     * (including newlines) to single spaces, then trim.
     */
    private fun normalizeWhitespace(text: String): String {
        return text.replace(Regex("""\s+"""), " ").trim()
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
        postTableFields: PostTableFields = PostTableFields(),
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
        var providerName: String? = null
        var specialInstructions: String? = null
        var examinerNameContact: String? = null
        var federalTaxId: String? = null
        var vendorNumber: String? = null

        // Post-table fields (special instructions and examiner info)
        postTableFields.specialInstructions?.let { specialInstructions = it }
        postTableFields.examinerNameContact?.let { examinerNameContact = it }

        // Fallback fields (lowest priority — last resort individual pattern matching)
        fallbackFields.caseId?.let { caseId = it }
        fallbackFields.requestId?.let { requestId = it }

        // Invoice fields (low-medium priority for overlapping fields)
        invoiceFields.providerName?.let { providerName = it }
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
            providerName = providerName,
            specialInstructions = specialInstructions,
            examinerNameContact = examinerNameContact,
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
        val providerName: String? = null,
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

    internal data class PostTableFields(
        val specialInstructions: String? = null,
        val examinerNameContact: String? = null,
    )
}

