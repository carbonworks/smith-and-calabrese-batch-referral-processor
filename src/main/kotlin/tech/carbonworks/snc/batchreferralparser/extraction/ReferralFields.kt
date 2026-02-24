package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Confidence level for a parsed field value.
 * HIGH: strong regex match or structured table extraction.
 * MEDIUM: partial match or secondary source.
 * LOW: weak heuristic match or fallback extraction.
 */
enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * Wrapper for an extracted field value with an associated confidence level.
 * A field may be absent (notFound) or present with HIGH, MEDIUM, or LOW confidence.
 */
data class ParsedField<T>(
    val value: T?,
    val confidence: Confidence?,
) {
    val isPresent: Boolean get() = value != null

    companion object {
        fun <T> notFound(): ParsedField<T> = ParsedField(null, null)
        fun <T> high(value: T): ParsedField<T> = ParsedField(value, Confidence.HIGH)
        fun <T> medium(value: T): ParsedField<T> = ParsedField(value, Confidence.MEDIUM)
        fun <T> low(value: T): ParsedField<T> = ParsedField(value, Confidence.LOW)
    }
}

/**
 * A single service line item from the Services Authorized section of a referral.
 * Only [cptCode] is required; all other fields are optional.
 */
data class ServiceLine(
    val cptCode: String,
    val procedureTypeCode: String? = null,
    val description: String? = null,
    val fee: String? = null,
)

/**
 * All target fields extracted from an SSA/DDS consultative examination referral PDF.
 *
 * Each scalar field is wrapped in [ParsedField] to carry extraction confidence.
 * Services are stored as a flat list with a separate [servicesConfidence] level.
 *
 * Use [hasLowConfidenceFields] to check whether any field was extracted with low
 * confidence, and [filledFieldCount] to count how many fields were successfully extracted.
 */
data class ReferralFields(
    // Claimant identity
    val firstName: ParsedField<String> = ParsedField.notFound(),
    val middleName: ParsedField<String> = ParsedField.notFound(),
    val lastName: ParsedField<String> = ParsedField.notFound(),

    // Case identification
    val caseId: ParsedField<String> = ParsedField.notFound(),
    val authorizationNumber: ParsedField<String> = ParsedField.notFound(),
    val requestId: ParsedField<String> = ParsedField.notFound(),

    // Dates
    val dateOfIssue: ParsedField<String> = ParsedField.notFound(),
    val dob: ParsedField<String> = ParsedField.notFound(),

    // Applicant (parent/guardian)
    val applicantName: ParsedField<String> = ParsedField.notFound(),

    // Appointment
    val appointmentDate: ParsedField<String> = ParsedField.notFound(),
    val appointmentTime: ParsedField<String> = ParsedField.notFound(),

    // Claimant address
    val streetAddress: ParsedField<String> = ParsedField.notFound(),
    val city: ParsedField<String> = ParsedField.notFound(),
    val state: ParsedField<String> = ParsedField.notFound(),
    val zipCode: ParsedField<String> = ParsedField.notFound(),

    // Contact
    val phone: ParsedField<String> = ParsedField.notFound(),

    // Services authorized
    val services: List<ServiceLine> = emptyList(),
    val servicesConfidence: Confidence? = null,

    // Provider/invoice
    val federalTaxId: ParsedField<String> = ParsedField.notFound(),
    val vendorNumber: ParsedField<String> = ParsedField.notFound(),

    // Footer/case components
    val caseNumberFullFooter: ParsedField<String> = ParsedField.notFound(),
    val assignedCode: ParsedField<String> = ParsedField.notFound(),
    val dccNumber: ParsedField<String> = ParsedField.notFound(),
) {
    /**
     * Returns true if any extracted field has LOW confidence, indicating
     * the extraction result may need manual review.
     */
    fun hasLowConfidenceFields(): Boolean {
        return allParsedFields().any { it.confidence == Confidence.LOW } ||
            servicesConfidence == Confidence.LOW
    }

    /**
     * Returns the count of fields that were successfully extracted (non-null value).
     * Services count as one field if the list is non-empty.
     */
    fun filledFieldCount(): Int {
        return allParsedFields().count { it.isPresent } +
            if (services.isNotEmpty()) 1 else 0
    }

    private fun allParsedFields(): List<ParsedField<*>> = listOf(
        firstName, middleName, lastName, caseId, authorizationNumber,
        requestId, dateOfIssue, dob, applicantName, appointmentDate,
        appointmentTime, streetAddress, city, state, zipCode, phone,
        federalTaxId, vendorNumber, caseNumberFullFooter, assignedCode, dccNumber,
    )
}
