package tech.carbonworks.snc.batchreferralparser.output

import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes extracted referral data to TSV (tab-separated values) files.
 *
 * Produces a standard TSV file with the same column layout as the XLSX and
 * CSV exports. Tabs, carriage returns, and newlines within field values are
 * replaced with spaces so the tab-delimited structure is preserved.
 *
 * Column order is determined by the [ExportColumnConfig] passed to [write].
 * Date fields are written as plain text strings (no Excel date formatting).
 */
object TsvWriter {

    private val FILENAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    /**
     * Sanitizes a field value for TSV output.
     *
     * Replaces tab characters, carriage returns, and newlines with spaces
     * so they do not break the tab-delimited row structure.
     *
     * @param value the raw field value
     * @return the sanitized value safe for TSV inclusion
     */
    internal fun sanitizeField(value: String): String {
        if (value.isEmpty()) return value
        return value
            .replace('\t', ' ')
            .replace('\r', ' ')
            .replace('\n', ' ')
    }

    /**
     * Writes referral data to a TSV file in the specified output directory.
     *
     * @param referrals the extracted referral data to write (may be empty)
     * @param outputDir the directory where the TSV file will be created
     * @param timestamp optional timestamp for the filename; defaults to now
     * @param columnConfig the column layout to use; defaults to the legacy column order
     * @return the [File] path of the written TSV file
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
        println("[TsvWriter] Writing ${referrals.size} referral(s): $enabledFieldCount field column(s), $spacerCount spacer column(s), expandServices=${columnConfig.expandServices}")

        val filename = "authorizations-${FILENAME_TIMESTAMP_FORMAT.format(timestamp)}.tsv"
        val outputFile = File(outputDir, filename)

        outputDir.mkdirs()

        FileWriter(outputFile, Charsets.UTF_8).use { writer ->
            // -- Header row --
            val headerLine = activeColumns.joinToString("\t") { column ->
                sanitizeField(
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
                    val dataLine = activeColumns.joinToString("\t") { column ->
                        if (column is ExportColumn.Spacer) {
                            ""
                        } else {
                            val field = column as ExportColumn.Field
                            val value = if (singleService != null && field.fieldId == "services") {
                                singleService
                            } else {
                                referral.getFieldValue(field.fieldId)
                            }
                            sanitizeField(value)
                        }
                    }
                    writer.write(dataLine)
                    writer.write("\r\n")
                }
            }
        }

        println("[TsvWriter] Wrote: ${outputFile.name} (${outputFile.length()} bytes)")
        return outputFile
    }
}
