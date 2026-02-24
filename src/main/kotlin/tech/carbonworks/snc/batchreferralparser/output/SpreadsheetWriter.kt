package tech.carbonworks.snc.batchreferralparser.output

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes extracted referral data to XLSX spreadsheets using Apache POI.
 *
 * Produces a plain XSSFWorkbook with simple cells (no macros, named ranges,
 * or data validation) to ensure Google Sheets compatibility. The header row
 * is bold and frozen.
 *
 * Column order mirrors [ReferralFields] property order, with a final
 * "Low Confidence Flag" column.
 */
object SpreadsheetWriter {

    private val FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /**
     * Column headings in output order. The final column is the low-confidence
     * flag; all others correspond to [ReferralFields] properties.
     */
    val COLUMN_HEADINGS: List<String> = listOf(
        "First Name",
        "Middle Name",
        "Last Name",
        "Case ID",
        "Authorization #",
        "Request ID",
        "Date of Issue",
        "DOB",
        "Applicant",
        "Appointment Date",
        "Appointment Time",
        "Street Address",
        "City",
        "State",
        "ZIP",
        "Phone",
        "Services",
        "Federal Tax ID",
        "Vendor Number",
        "Case Number (Footer)",
        "Assigned Code",
        "DCC Number",
        "Low Confidence Flag",
    )

    /**
     * Writes referral data to an XLSX file in the specified output directory.
     *
     * @param referrals the extracted referral data to write (may be empty)
     * @param outputDir the directory where the XLSX file will be created
     * @param timestamp optional timestamp for the filename; defaults to now
     * @return the [File] path of the written spreadsheet
     */
    fun write(
        referrals: List<ReferralFields>,
        outputDir: File,
        timestamp: LocalDateTime = LocalDateTime.now(),
    ): File {
        val filename = "patient-referrals-${FILENAME_TIMESTAMP_FORMAT.format(timestamp)}.xlsx"
        val outputFile = File(outputDir, filename)

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Referrals")

            // -- Header row (bold + frozen) --
            val headerStyle = workbook.createCellStyle().apply {
                val boldFont = workbook.createFont().apply {
                    bold = true
                }
                setFont(boldFont)
            }

            val headerRow = sheet.createRow(0)
            COLUMN_HEADINGS.forEachIndexed { colIndex, heading ->
                val cell = headerRow.createCell(colIndex)
                cell.setCellValue(heading)
                cell.cellStyle = headerStyle
            }

            // Freeze the header row
            sheet.createFreezePane(0, 1)

            // -- Data rows --
            referrals.forEachIndexed { rowIndex, referral ->
                val row = sheet.createRow(rowIndex + 1)
                val values = extractRowValues(referral)
                values.forEachIndexed { colIndex, value ->
                    if (value.isNotEmpty()) {
                        row.createCell(colIndex).setCellValue(value)
                    }
                }
            }

            // Write to file
            outputDir.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                workbook.write(fos)
            }
        }

        return outputFile
    }

    /**
     * Extracts cell values from a [ReferralFields] instance in column order.
     * Returns a list of strings (empty string for absent fields).
     */
    private fun extractRowValues(referral: ReferralFields): List<String> {
        val services = referral.services.joinToString(", ") { it.cptCode }
        val lowConfidenceFlag = if (referral.hasLowConfidenceFields()) "YES" else ""

        return listOf(
            referral.firstName.value.orEmpty(),
            referral.middleName.value.orEmpty(),
            referral.lastName.value.orEmpty(),
            referral.caseId.value.orEmpty(),
            referral.authorizationNumber.value.orEmpty(),
            referral.requestId.value.orEmpty(),
            referral.dateOfIssue.value.orEmpty(),
            referral.dob.value.orEmpty(),
            referral.applicantName.value.orEmpty(),
            referral.appointmentDate.value.orEmpty(),
            referral.appointmentTime.value.orEmpty(),
            referral.streetAddress.value.orEmpty(),
            referral.city.value.orEmpty(),
            referral.state.value.orEmpty(),
            referral.zipCode.value.orEmpty(),
            referral.phone.value.orEmpty(),
            services,
            referral.federalTaxId.value.orEmpty(),
            referral.vendorNumber.value.orEmpty(),
            referral.caseNumberFullFooter.value.orEmpty(),
            referral.assignedCode.value.orEmpty(),
            referral.dccNumber.value.orEmpty(),
            lowConfidenceFlag,
        )
    }
}
