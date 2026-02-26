package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Structured warning from a parsing stage indicating that expected data
 * could not be extracted.
 *
 * These replace ad-hoc `println` diagnostics with a typed representation
 * that the UI can display to help users understand what was and wasn't
 * extracted from each PDF.
 *
 * @param field human-readable field name, e.g. "Case ID", "Federal Tax ID"
 * @param stage extraction stage that produced the warning, e.g. "header", "invoice", "footer", "table"
 * @param message description of why the field was not extracted,
 *        e.g. "Label found but pattern did not match"
 */
data class ParsingWarning(
    val field: String,
    val stage: String,
    val message: String,
)
