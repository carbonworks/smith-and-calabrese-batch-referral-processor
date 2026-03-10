package tech.carbonworks.snc.batchreferralparser.output

enum class ExportFormat(val displayName: String, val extension: String) {
    XLSX("Excel (.xlsx)", "xlsx"),
    CSV("CSV (.csv)", "csv"),
    TSV("TSV (.tsv)", "tsv"),
}
