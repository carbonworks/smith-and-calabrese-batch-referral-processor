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
 * Column order is determined by the [ExportColumnConfig] passed to [write].
 * When no config is provided, the default config produces output identical
 * to the original hardcoded column layout.
 */
object SpreadsheetWriter {

    private val FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

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
     * Column headings in output order. Each corresponds to a [ReferralFields] property.
     *
     * Retained for backward compatibility with existing tests and external callers.
     * New code should use [ExportColumnConfig.default] instead.
     */
    val COLUMN_HEADINGS: List<String> = ExportColumnConfig.default().columns.map { column ->
        when (column) {
            is ExportColumn.Field -> column.displayName
            is ExportColumn.Spacer -> ""
        }
    }

    /**
     * Writes referral data to an XLSX file in the specified output directory.
     *
     * @param referrals the extracted referral data to write (may be empty)
     * @param outputDir the directory where the XLSX file will be created
     * @param timestamp optional timestamp for the filename; defaults to now
     * @param columnConfig the column layout to use; defaults to the legacy column order
     * @return the [File] path of the written spreadsheet
     */
    fun write(
        referrals: List<ReferralFields>,
        outputDir: File,
        timestamp: LocalDateTime = LocalDateTime.now(),
        columnConfig: ExportColumnConfig = ExportColumnConfig.default(),
    ): File {
        // Filter to enabled fields + all spacers
        val activeColumns = columnConfig.columns.filter { column ->
            when (column) {
                is ExportColumn.Field -> column.enabled
                is ExportColumn.Spacer -> true
            }
        }

        val filename = "authorizations-${FILENAME_TIMESTAMP_FORMAT.format(timestamp)}.xlsx"
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
            activeColumns.forEachIndexed { colIndex, column ->
                val cell = headerRow.createCell(colIndex)
                cell.setCellValue(
                    when (column) {
                        is ExportColumn.Field -> column.displayName
                        is ExportColumn.Spacer -> ""
                    }
                )
                cell.cellStyle = headerStyle
            }

            // Freeze the header row
            sheet.createFreezePane(0, 1)

            // -- Data rows --
            // Build the flat list of data rows. When expandServices is enabled
            // and a referral has multiple services, each service gets its own
            // row with all other field values duplicated.
            val expandServices = columnConfig.expandServices
            val servicesFieldActive = activeColumns.any {
                it is ExportColumn.Field && it.fieldId == "services"
            }

            var currentRow = 1
            for (referral in referrals) {
                val servicesCpt = referral.services.map { it.cptCode }
                val shouldExpand = expandServices && servicesFieldActive && servicesCpt.size > 1

                val serviceValues = if (shouldExpand) servicesCpt else listOf(null)

                for (singleService in serviceValues) {
                    val row = sheet.createRow(currentRow++)
                    activeColumns.forEachIndexed { colIndex, column ->
                        if (column is ExportColumn.Spacer) {
                            // Spacer columns produce empty cells (no cell created)
                            return@forEachIndexed
                        }

                        val field = column as ExportColumn.Field
                        val value = if (singleService != null && field.fieldId == "services") {
                            singleService
                        } else {
                            referral.getFieldValue(field.fieldId)
                        }

                        if (value.isNotEmpty()) {
                            if (field.fieldId in DATE_FIELD_IDS) {
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
            }

            // Write to file
            outputDir.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                workbook.write(fos)
            }
        }

        return outputFile
    }
}
