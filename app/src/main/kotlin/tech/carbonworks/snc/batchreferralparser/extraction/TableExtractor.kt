package tech.carbonworks.snc.batchreferralparser.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import technology.tabula.ObjectExtractor
import technology.tabula.Page
import technology.tabula.RectangularTextContainer
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
        println("[TableExtractor] Opening: ${file.name}")
        val document: PDDocument
        try {
            document = Loader.loadPDF(file)
        } catch (e: Exception) {
            println("[TableExtractor] Failed to load PDF: ${file.name} — ${e.message}")
            return emptyList()
        }

        return document.use { doc ->
            try {
                val extractor = ObjectExtractor(doc)
                extractor.use { oe ->
                    val results = mutableListOf<ExtractedTable>()
                    val pageCount = doc.numberOfPages
                    println("[TableExtractor] Scanning $pageCount page(s) for tables: ${file.name}")

                    for (pageIndex in 1..pageCount) {
                        val page: Page = try {
                            oe.extract(pageIndex)
                        } catch (e: Exception) {
                            println("[TableExtractor] Failed to extract page $pageIndex: ${e.message}")
                            continue
                        }

                        val tables = extractTablesFromPage(page)
                        for (table in tables) {
                            val extracted = convertTable(table, pageIndex)
                            if (extracted.cells.isNotEmpty()) {
                                results.add(extracted)
                                println("[TableExtractor] Page $pageIndex: table found — ${extracted.rowCount} row(s), ${extracted.columnCount} col(s), ${extracted.cells.size} cell(s)")
                            }
                        }
                        if (tables.isEmpty()) {
                            println("[TableExtractor] Page $pageIndex: no tables found")
                        }
                    }

                    println("[TableExtractor] Total: ${results.size} table(s) from ${file.name}")
                    results
                }
            } catch (e: Exception) {
                println("[TableExtractor] Extraction error: ${file.name} — ${e.message}")
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
            val filtered = latticeTables.filter { it.rowCount > 0 }
            println("[TableExtractor] Lattice strategy succeeded: ${filtered.size} table(s)")
            return filtered
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

        println("[TableExtractor] Lattice found nothing, falling back to stream strategy")
        return try {
            val streamTables = streamExtractor.extract(page)
            val filtered = streamTables.filter { it.rowCount > 0 }
            println("[TableExtractor] Stream strategy: ${filtered.size} table(s)")
            filtered
        } catch (e: Exception) {
            println("[TableExtractor] Stream strategy failed: ${e.message}")
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
                val content = extractCellText(cellContainer)
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

    /**
     * Extract text from a Tabula cell container, preserving line breaks.
     *
     * Tabula's [RectangularTextContainer.getText] concatenates all child text elements
     * with spaces, destroying the multi-line structure present in PDF cells. This method
     * reconstructs line breaks by examining the Y-coordinates of child text elements.
     * Elements with a significant vertical gap (> [LINE_Y_TOLERANCE] points) are separated
     * by newlines instead of spaces.
     *
     * Falls back to [RectangularTextContainer.getText] when child elements are unavailable.
     */
    private fun extractCellText(cell: RectangularTextContainer<*>?): String {
        if (cell == null) return ""

        val elements = try {
            cell.textElements
        } catch (e: Exception) {
            null
        }

        if (elements.isNullOrEmpty()) {
            return cell.text?.trim() ?: ""
        }

        // Each element is a RectangularTextContainer subtype with positional data
        // (top/left from Rectangle2D) and text (from HasText). We use the generic
        // list and access position/text through the base class methods.
        data class PositionedText(val top: Float, val left: Float, val text: String)

        val positioned = elements.mapNotNull { elem ->
            if (elem is technology.tabula.HasText) {
                val rect = elem as java.awt.geom.Rectangle2D
                PositionedText(
                    top = rect.y.toFloat(),
                    left = rect.x.toFloat(),
                    text = (elem as technology.tabula.HasText).text ?: "",
                )
            } else {
                null
            }
        }

        if (positioned.isEmpty()) {
            return cell.text?.trim() ?: ""
        }

        // Group text elements by Y position (with tolerance for minor jitter).
        // Elements within LINE_Y_TOLERANCE points of each other are on the same line.
        data class LineGroup(val yBaseline: Float, val elements: MutableList<PositionedText>)

        val groups = mutableListOf<LineGroup>()

        for (pt in positioned) {
            val matchingGroup = groups.find {
                kotlin.math.abs(it.yBaseline - pt.top) <= LINE_Y_TOLERANCE
            }
            if (matchingGroup != null) {
                matchingGroup.elements.add(pt)
            } else {
                groups.add(LineGroup(pt.top, mutableListOf(pt)))
            }
        }

        // Sort groups by Y position (top-to-bottom), then elements within each
        // group by X position (left-to-right)
        groups.sortBy { it.yBaseline }
        for (group in groups) {
            group.elements.sortBy { it.left }
        }

        // Join elements within each group (no extra separator — Tabula elements
        // already include spacing), then join groups with newlines
        return groups.joinToString("\n") { group ->
            group.elements.joinToString("") { it.text }
        }.trim()
    }

    companion object {
        /**
         * Y-coordinate tolerance in PDF points for grouping text elements on the
         * same line. Elements within this many points of each other vertically are
         * considered part of the same line.
         */
        private const val LINE_Y_TOLERANCE = 2.5f
    }
}
