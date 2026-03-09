package tech.carbonworks.snc.batchreferralparser.extraction

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
 * Each scalar field is a nullable [String]. Services are stored as a flat list.
 *
 * Use [filledFieldCount] to count how many fields were successfully extracted.
 */
data class ReferralFields(
    // Claimant identity
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,

    // Case identification
    val caseId: String? = null,
    val authorizationNumber: String? = null,
    val requestId: String? = null,

    // Dates
    val dateOfIssue: String? = null,
    val dob: String? = null,

    // Applicant (parent/guardian)
    val applicantName: String? = null,

    // Appointment
    val appointmentDate: String? = null,
    val appointmentTime: String? = null,

    // Claimant address
    val streetAddress: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,

    // Contact
    val phone: String? = null,

    // Services authorized
    val services: List<ServiceLine> = emptyList(),

    // Provider/invoice
    val providerName: String? = null,
    val federalTaxId: String? = null,
    val vendorNumber: String? = null,

    // Post-table fields
    val specialInstructions: String? = null,
    val examinerNameContact: String? = null,

    // Footer/case components
    val caseNumberFullFooter: String? = null,
    val assignedCode: String? = null,
    val dccNumber: String? = null,
) {
    /**
     * Returns the count of fields that were successfully extracted (non-null value).
     * Services count as one field if the list is non-empty.
     */
    fun filledFieldCount(): Int {
        return listOf(
            firstName, middleName, lastName, caseId, authorizationNumber,
            requestId, dateOfIssue, dob, applicantName, appointmentDate,
            appointmentTime, streetAddress, city, state, zipCode, phone,
            providerName, specialInstructions, examinerNameContact,
            federalTaxId, vendorNumber, caseNumberFullFooter, assignedCode, dccNumber,
        ).count { it != null } +
            if (services.isNotEmpty()) 1 else 0
    }
}
