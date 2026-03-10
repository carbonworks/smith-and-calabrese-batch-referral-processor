package tech.carbonworks.snc.batchreferralparser.output

/**
 * Supported export file formats for referral data output.
 *
 * @param displayName human-readable name shown in the UI dropdown
 * @param extension file extension (without dot) used for filenames and save dialogs
 */
enum class ExportFormat(val displayName: String, val extension: String) {
    XLSX("XLSX", "xlsx"),
    CSV("CSV", "csv"),
    TSV("TSV", "tsv"),
}
