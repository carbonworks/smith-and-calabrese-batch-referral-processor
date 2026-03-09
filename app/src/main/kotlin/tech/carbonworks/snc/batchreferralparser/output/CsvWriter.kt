package tech.carbonworks.snc.batchreferralparser.output

import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes extracted referral data to CSV files using RFC 4180 formatting.
 *
 * Produces a standard CSV file with the same column layout as the XLSX export.
 * Fields containing commas, double quotes, or newlines are enclosed in double
 * quotes, and embedded double quotes are escaped by doubling them.
 *
 * Column order is determined by the [ExportColumnConfig] passed to [write].
 * Date fields are written as plain text strings (no Excel date formatting).
 */
object CsvWriter {

    private val FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /**
     * Escapes a field value according to RFC 4180.
     *
     * Fields that contain commas, double quotes, or newlines are enclosed in
     * double quotes. Embedded double quotes are escaped by doubling them.
     *
     * @param value the raw field value
     * @return the RFC 4180-compliant escaped value
     */
    internal fun escapeField(value: String): String {
        if (value.isEmpty()) return value
        val needsQuoting = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * Writes referral data to a CSV file in the specified output directory.
     *
     * @param referrals the extracted referral data to write (may be empty)
     * @param outputDir the directory where the CSV file will be created
     * @param timestamp optional timestamp for the filename; defaults to now
     * @param columnConfig the column layout to use; defaults to the legacy column order
     * @return the [File] path of the written CSV file
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

        val enabledFieldCount = activeColumns.count { it is ExportColumn.Field }
        val spacerCount = activeColumns.count { it is ExportColumn.Spacer }
        println("[CsvWriter] Writing ${referrals.size} referral(s): $enabledFieldCount field column(s), $spacerCount spacer column(s), expandServices=${columnConfig.expandServices}")

        val filename = "authorizations-${FILENAME_TIMESTAMP_FORMAT.format(timestamp)}.csv"
        val outputFile = File(outputDir, filename)

        outputDir.mkdirs()

        FileWriter(outputFile, Charsets.UTF_8).use { writer ->
            // -- Header row --
            val headerLine = activeColumns.joinToString(",") { column ->
                escapeField(
                    when (column) {
                        is ExportColumn.Field -> column.displayName
                        is ExportColumn.Spacer -> ""
                    }
                )
            }
            writer.write(headerLine)
            writer.write("\r\n")

            // -- Data rows --
            val expandServices = columnConfig.expandServices
            val servicesFieldActive = activeColumns.any {
                it is ExportColumn.Field && it.fieldId == "services"
            }

            for (referral in referrals) {
                val servicesCpt = referral.services.map { it.cptCode }
                val shouldExpand = expandServices && servicesFieldActive && servicesCpt.size > 1

                val serviceValues = if (shouldExpand) servicesCpt else listOf(null)

                for (singleService in serviceValues) {
                    val dataLine = activeColumns.joinToString(",") { column ->
                        if (column is ExportColumn.Spacer) {
                            ""
                        } else {
                            val field = column as ExportColumn.Field
                            val value = if (singleService != null && field.fieldId == "services") {
                                singleService
                            } else {
                                referral.getFieldValue(field.fieldId)
                            }
                            escapeField(value)
                        }
                    }
                    writer.write(dataLine)
                    writer.write("\r\n")
                }
            }
        }

        println("[CsvWriter] Wrote: ${outputFile.name} (${outputFile.length()} bytes)")
        return outputFile
    }
}
