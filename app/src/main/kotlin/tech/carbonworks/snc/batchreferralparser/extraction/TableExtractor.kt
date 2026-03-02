package tech.carbonworks.snc.batchreferralparser.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import technology.tabula.ObjectExtractor
import technology.tabula.Page
import technology.tabula.Table
import technology.tabula.extractors.BasicExtractionAlgorithm
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm
import java.io.File

/**
 * A single cell within an extracted table, identified by its row and column position.
 */
data class TableCell(
    val content: String,
    val rowIndex: Int,
    val columnIndex: Int,
)

/**
 * A table extracted from a PDF page, containing structured cell data.
 */
data class ExtractedTable(
    val pageNumber: Int,
    val cells: List<TableCell>,
    val rowCount: Int,
    val columnCount: Int,
)

/**
 * Extracts structured table data from PDF files using Tabula-java.
 *
 * Uses a lattice-first, stream-fallback strategy:
 * 1. First attempts SpreadsheetExtractionAlgorithm (lattice) for tables with drawn cell borders
 * 2. Falls back to BasicExtractionAlgorithm (stream) for tables without clear borders
 *
 * Returns an empty list for PDFs with no tables or on any extraction failure.
 */
class TableExtractor {

    private val latticeExtractor = SpreadsheetExtractionAlgorithm()
    private val streamExtractor = BasicExtractionAlgorithm()

    /**
     * Extract all tables from the given PDF file.
     *
     * @param file the PDF file to extract tables from
     * @return a list of [ExtractedTable] objects, one per detected table; empty if no tables found
     *         or if the file cannot be read
     */
    fun extract(file: File): List<ExtractedTable> {
        val document: PDDocument
        try {
            document = Loader.loadPDF(file)
        } catch (e: Exception) {
            return emptyList()
        }

        return document.use { doc ->
            try {
                val extractor = ObjectExtractor(doc)
                extractor.use { oe ->
                    val results = mutableListOf<ExtractedTable>()
                    val pageCount = doc.numberOfPages

                    for (pageIndex in 1..pageCount) {
                        val page: Page = try {
                            oe.extract(pageIndex)
                        } catch (e: Exception) {
                            continue
                        }

                        val tables = extractTablesFromPage(page)
                        for (table in tables) {
                            val extracted = convertTable(table, pageIndex)
                            if (extracted.cells.isNotEmpty()) {
                                results.add(extracted)
                            }
                        }
                    }

                    results
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Extract tables from a single page using lattice-first, stream-fallback strategy.
     *
     * The stream fallback is only used when the page has some ruling lines (indicating
     * partial table structure) but not enough for lattice extraction to succeed. Pages
     * with no ruling lines at all are treated as non-tabular.
     */
    private fun extractTablesFromPage(page: Page): List<Table> {
        // Step 1: Try lattice extraction (tables with drawn borders).
        // SpreadsheetExtractionAlgorithm.extract(page) handles its own detection
        // of ruling lines and cell boundaries internally.
        val latticeTables = try {
            latticeExtractor.extract(page)
        } catch (e: Exception) {
            emptyList()
        }

        if (latticeTables.isNotEmpty() && latticeTables.any { it.rowCount > 0 }) {
            return latticeTables.filter { it.rowCount > 0 }
        }

        // Step 2: Fall back to stream extraction only if the page has ruling lines.
        // Pages with no rulings at all are plain text and should not be treated as tables.
        val hasRulings = try {
            page.rulings.isNotEmpty()
        } catch (e: Exception) {
            false
        }

        if (!hasRulings) {
            return emptyList()
        }

        return try {
            val streamTables = streamExtractor.extract(page)
            streamTables.filter { it.rowCount > 0 }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Convert a Tabula [Table] to our [ExtractedTable] data model.
     *
     * Tabula's Table has rows of RectangularTextContainer cells.
     * We flatten these into a list of [TableCell] objects with row/column indices.
     */
    private fun convertTable(table: Table, pageNumber: Int): ExtractedTable {
        val cells = mutableListOf<TableCell>()
        val rowCount = table.rowCount
        val colCount = table.colCount

        for (rowIndex in 0 until rowCount) {
            val row = try {
                table.rows[rowIndex]
            } catch (e: Exception) {
                continue
            }

            for (colIndex in row.indices) {
                val cellContainer = row[colIndex]
                val content = cellContainer?.text?.trim() ?: ""
                cells.add(
                    TableCell(
                        content = content,
                        rowIndex = rowIndex,
                        columnIndex = colIndex,
                    )
                )
            }
        }

        return ExtractedTable(
            pageNumber = pageNumber,
            cells = cells,
            rowCount = rowCount,
            columnCount = colCount,
        )
    }
}
