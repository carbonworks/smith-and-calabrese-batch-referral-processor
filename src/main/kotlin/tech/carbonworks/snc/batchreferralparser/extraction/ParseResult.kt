package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Result of parsing a single PDF through [FieldParser.parse].
 *
 * Wraps the extracted [ReferralFields] together with any [ParsingWarning]s
 * generated during extraction. Warnings indicate fields whose labels were
 * detected in the text but whose values could not be extracted by the
 * corresponding regex patterns.
 *
 * @param fields the extracted referral data
 * @param warnings structured warnings from each extraction stage
 */
data class ParseResult(
    val fields: ReferralFields,
    val warnings: List<ParsingWarning>,
)
