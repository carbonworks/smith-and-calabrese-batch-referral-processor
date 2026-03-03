package tech.carbonworks.snc.batchreferralparser.output

import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields

/**
 * A single column definition for XLSX export.
 *
 * @property fieldId machine identifier matching a [ReferralFields] property (null for spacers)
 * @property displayName human-readable column heading (blank for spacers)
 * @property enabled whether this column appears in the output
 * @property isSpacer true if this column is a visual spacer (empty heading + empty cells)
 */
data class ExportColumn(
    val fieldId: String?,
    val displayName: String,
    val enabled: Boolean = true,
    val isSpacer: Boolean = false,
)

/**
 * Ordered list of columns that defines XLSX export layout.
 *
 * @property columns the full ordered column list (may include disabled entries and spacers)
 */
data class ExportColumnConfig(
    val columns: List<ExportColumn>,
) {
    companion object {
        /**
         * Returns the default column configuration that exactly matches the
         * legacy hardcoded [SpreadsheetWriter.COLUMN_HEADINGS] order.
         */
        fun default(): ExportColumnConfig = ExportColumnConfig(
            columns = listOf(
                ExportColumn(fieldId = "firstName", displayName = "First Name"),
                ExportColumn(fieldId = "middleName", displayName = "Middle Name"),
                ExportColumn(fieldId = "lastName", displayName = "Last Name"),
                ExportColumn(fieldId = "caseId", displayName = "Case ID"),
                ExportColumn(fieldId = "authorizationNumber", displayName = "Authorization #"),
                ExportColumn(fieldId = "requestId", displayName = "Request ID"),
                ExportColumn(fieldId = "dateOfIssue", displayName = "Date of Issue"),
                ExportColumn(fieldId = "dob", displayName = "DOB"),
                ExportColumn(fieldId = "applicantName", displayName = "Applicant"),
                ExportColumn(fieldId = "appointmentDate", displayName = "Appointment Date"),
                ExportColumn(fieldId = "appointmentTime", displayName = "Appointment Time"),
                ExportColumn(fieldId = "streetAddress", displayName = "Street Address"),
                ExportColumn(fieldId = "city", displayName = "City"),
                ExportColumn(fieldId = "state", displayName = "State"),
                ExportColumn(fieldId = "zipCode", displayName = "ZIP"),
                ExportColumn(fieldId = "phone", displayName = "Phone"),
                ExportColumn(fieldId = "services", displayName = "Services"),
                ExportColumn(fieldId = "federalTaxId", displayName = "Federal Tax ID"),
                ExportColumn(fieldId = "vendorNumber", displayName = "Vendor Number"),
                ExportColumn(fieldId = "caseNumberFullFooter", displayName = "Case Number (Footer)"),
                ExportColumn(fieldId = "assignedCode", displayName = "Assigned Code"),
                ExportColumn(fieldId = "dccNumber", displayName = "DCC Number"),
            ),
        )
    }
}

/**
 * Field IDs that represent date values. Used by [SpreadsheetWriter] to decide
 * whether to attempt date parsing and write an Excel date cell.
 */
val DATE_FIELD_IDS: Set<String> = setOf("dateOfIssue", "dob", "appointmentDate")

/**
 * Retrieves the value of a field on [ReferralFields] by its string [fieldId].
 *
 * For the special `"services"` field, returns a comma-separated list of CPT codes.
 * Returns an empty string for unknown field IDs or null field values.
 */
fun ReferralFields.getFieldValue(fieldId: String): String {
    return when (fieldId) {
        "firstName" -> firstName.orEmpty()
        "middleName" -> middleName.orEmpty()
        "lastName" -> lastName.orEmpty()
        "caseId" -> caseId.orEmpty()
        "authorizationNumber" -> authorizationNumber.orEmpty()
        "requestId" -> requestId.orEmpty()
        "dateOfIssue" -> dateOfIssue.orEmpty()
        "dob" -> dob.orEmpty()
        "applicantName" -> applicantName.orEmpty()
        "appointmentDate" -> appointmentDate.orEmpty()
        "appointmentTime" -> appointmentTime.orEmpty()
        "streetAddress" -> streetAddress.orEmpty()
        "city" -> city.orEmpty()
        "state" -> state.orEmpty()
        "zipCode" -> zipCode.orEmpty()
        "phone" -> phone.orEmpty()
        "services" -> services.joinToString(", ") { it.cptCode }
        "federalTaxId" -> federalTaxId.orEmpty()
        "vendorNumber" -> vendorNumber.orEmpty()
        "caseNumberFullFooter" -> caseNumberFullFooter.orEmpty()
        "assignedCode" -> assignedCode.orEmpty()
        "dccNumber" -> dccNumber.orEmpty()
        else -> ""
    }
}
