package tech.carbonworks.snc.batchreferralparser.output

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields

/**
 * Canonical mapping of field IDs to display names, in the default column order.
 *
 * This is the single source of truth for which fields exist and what they are
 * called in the spreadsheet header row. The order here matches the original
 * [SpreadsheetWriter.COLUMN_HEADINGS] order.
 */
val DEFAULT_FIELD_ORDER: List<Pair<String, String>> = listOf(
    "firstName" to "First Name",
    "middleName" to "Middle Name",
    "lastName" to "Last Name",
    "caseId" to "Case ID",
    "authorizationNumber" to "Authorization #",
    "requestId" to "Request ID",
    "dateOfIssue" to "Date of Issue",
    "dob" to "DOB",
    "applicantName" to "Applicant",
    "appointmentDate" to "Appointment Date",
    "appointmentTime" to "Appointment Time",
    "streetAddress" to "Street Address",
    "city" to "City",
    "state" to "State",
    "zipCode" to "ZIP",
    "phone" to "Phone",
    "services" to "Services",
    "specialInstructions" to "Special Instructions",
    "examinerNameContact" to "Examiner Name/Contact",
    "federalTaxId" to "Federal Tax ID",
    "vendorNumber" to "Vendor Number",
    "caseNumberFullFooter" to "Case Number (Footer)",
    "assignedCode" to "Assigned Code",
    "dccNumber" to "DCC Number",
)

/**
 * Field IDs whose values represent dates and should be written as Excel date
 * cells rather than plain text.
 *
 * Replaces the fragile index-based [SpreadsheetWriter.DATE_COLUMN_INDICES]
 * approach with an explicit, name-based set.
 */
val DATE_FIELD_IDS: Set<String> = setOf("dateOfIssue", "dob", "appointmentDate")

/**
 * A single column in the configurable export layout.
 *
 * Sealed hierarchy with two variants:
 * - [Field] — a data field backed by a [ReferralFields] property.
 * - [Spacer] — an empty column used for visual separation in the spreadsheet.
 *
 * All subclasses are serializable via `kotlinx.serialization` so the user's
 * column configuration can be persisted as JSON in [java.util.prefs.Preferences].
 */
@Serializable
sealed class ExportColumn {

    /**
     * A data field column that maps to a [ReferralFields] property.
     *
     * @property fieldId the programmatic identifier matching [DEFAULT_FIELD_ORDER] keys
     * @property displayName the human-readable column header
     * @property enabled whether this column is included in the export (can be toggled off)
     */
    @Serializable
    @SerialName("field")
    data class Field(
        val fieldId: String,
        val displayName: String,
        val enabled: Boolean = true,
    ) : ExportColumn()

    /**
     * A blank spacer column for visual separation in the spreadsheet.
     *
     * @property id unique identifier for this spacer (used for drag-drop identity)
     * @property label display label shown in the configuration UI
     */
    @Serializable
    @SerialName("spacer")
    data class Spacer(
        val id: String,
        val label: String = "Empty Column",
    ) : ExportColumn()
}

/**
 * The complete export column configuration: an ordered list of columns that
 * determines which fields appear in the XLSX output and in what order.
 *
 * Use [ExportColumnConfig.default] to obtain the canonical 24-field layout.
 */
@Serializable
data class ExportColumnConfig(
    val columns: List<ExportColumn>,
    val expandServices: Boolean = false,
) {
    companion object {
        /**
         * Returns the default column configuration with all 24 fields enabled,
         * in the canonical order defined by [DEFAULT_FIELD_ORDER].
         */
        fun default(): ExportColumnConfig = ExportColumnConfig(
            columns = DEFAULT_FIELD_ORDER.map { (fieldId, displayName) ->
                ExportColumn.Field(fieldId = fieldId, displayName = displayName)
            },
            expandServices = false,
        )
    }
}

/**
 * Returns the extracted value for the given [fieldId] from this [ReferralFields].
 *
 * The `services` field is flattened to a comma-separated string of CPT codes.
 * Unrecognized field IDs return an empty string.
 */
fun ReferralFields.getFieldValue(fieldId: String): String = when (fieldId) {
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
    "specialInstructions" -> specialInstructions.orEmpty()
    "examinerNameContact" -> examinerNameContact.orEmpty()
    "federalTaxId" -> federalTaxId.orEmpty()
    "vendorNumber" -> vendorNumber.orEmpty()
    "caseNumberFullFooter" -> caseNumberFullFooter.orEmpty()
    "assignedCode" -> assignedCode.orEmpty()
    "dccNumber" -> dccNumber.orEmpty()
    else -> ""
}
