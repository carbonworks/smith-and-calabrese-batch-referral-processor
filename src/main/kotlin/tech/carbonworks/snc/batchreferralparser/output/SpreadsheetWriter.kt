package tech.carbonworks.snc.batchreferralparser.output

import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

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

    /** Column indices that contain date values and should be written as Excel date cells. */
    private val DATE_COLUMN_INDICES = setOf(6, 7, 9) // Date of Issue, DOB, Appointment Date

    /**
     * Regex to strip weekday prefixes (e.g., "Thursday ") and ordinal suffixes
     * (e.g., "5th" -> "5") from date strings before parsing.
     */
    private val WEEKDAY_PREFIX = Regex("^(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\s+", RegexOption.IGNORE_CASE)
    private val ORDINAL_SUFFIX = Regex("(\\d{1,2})(?:st|nd|rd|th)", RegexOption.IGNORE_CASE)

    /**
     * Date formatters to attempt when parsing date strings, in priority order.
     * Uses lenient month-day parsing to handle single-digit months/days.
     */
    private val DATE_FORMATTERS: List<DateTimeFormatter> = listOf(
        // "MMMM d, yyyy" — e.g., "August 13, 2024" or "August 1, 2015"
        DateTimeFormatterBuilder()
            .appendPattern("MMMM")
            .appendLiteral(' ')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral(", ")
            .appendPattern("yyyy")
            .toFormatter(Locale.ENGLISH),
        // "MM/dd/yyyy" and "M/d/yyyy" — e.g., "09/15/2024" or "1/5/2025"
        DateTimeFormatterBuilder()
            .appendValue(ChronoField.MONTH_OF_YEAR)
            .appendLiteral('/')
            .appendValue(ChronoField.DAY_OF_MONTH)
            .appendLiteral('/')
            .appendPattern("yyyy")
            .toFormatter(Locale.ENGLISH),
    )

    /**
     * Attempts to parse a date string into a [LocalDate] using known formats.
     * Handles weekday prefixes and ordinal suffixes before parsing.
     *
     * @return the parsed [LocalDate], or null if no format matches
     */
    private fun tryParseDate(text: String): LocalDate? {
        if (text.isBlank()) return null

        // Strip weekday prefix and ordinal suffix
        val cleaned = text
            .let { WEEKDAY_PREFIX.replace(it, "") }
            .let { ORDINAL_SUFFIX.replace(it, "$1") }
            .trim()

        for (formatter in DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, formatter)
            } catch (_: DateTimeParseException) {
                // Try next format
            }
        }
        return null
    }

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

            // -- Date cell style (mm/dd/yyyy for Google Sheets compatibility) --
            val dateStyle: XSSFCellStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("mm/dd/yyyy")
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
                        if (colIndex in DATE_COLUMN_INDICES) {
                            val parsedDate = tryParseDate(value)
                            if (parsedDate != null) {
                                val cell = row.createCell(colIndex)
                                cell.setCellValue(java.sql.Date.valueOf(parsedDate))
                                cell.cellStyle = dateStyle
                            } else {
                                row.createCell(colIndex).setCellValue(value)
                            }
                        } else {
                            row.createCell(colIndex).setCellValue(value)
                        }
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
