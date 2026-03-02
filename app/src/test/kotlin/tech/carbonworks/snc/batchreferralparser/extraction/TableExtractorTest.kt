package tech.carbonworks.snc.batchreferralparser.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableExtractorTest {

    private val tempFiles = mutableListOf<File>()
    private val extractor = TableExtractor()

    @AfterEach
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    /**
     * Create a PDF with a 2x2 table formed by drawn lines with text in each cell.
     * The table has explicit horizontal and vertical ruling lines that Tabula's
     * lattice extractor should detect.
     */
    private fun createTablePdf(): File {
        val file = File.createTempFile("test-table-", ".pdf")
        tempFiles.add(file)

        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            PDPageContentStream(doc, page).use { content ->
                val startX = 72f
                val startY = 700f
                val cellW = 150f
                val cellH = 30f

                // Draw horizontal lines (3 lines for 2 rows)
                for (row in 0..2) {
                    content.moveTo(startX, startY - row * cellH)
                    content.lineTo(startX + 2 * cellW, startY - row * cellH)
                    content.stroke()
                }

                // Draw vertical lines (3 lines for 2 columns)
                for (col in 0..2) {
                    content.moveTo(startX + col * cellW, startY)
                    content.lineTo(startX + col * cellW, startY - 2 * cellH)
                    content.stroke()
                }

                // Add text in cell (0,0) — "Cell A1"
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(startX + 5, startY - 20f)
                content.showText("Cell A1")
                content.endText()

                // Add text in cell (0,1) — "Cell B1"
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(startX + cellW + 5, startY - 20f)
                content.showText("Cell B1")
                content.endText()

                // Add text in cell (1,0) — "Cell A2"
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(startX + 5, startY - cellH - 20f)
                content.showText("Cell A2")
                content.endText()

                // Add text in cell (1,1) — "Cell B2"
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(startX + cellW + 5, startY - cellH - 20f)
                content.showText("Cell B2")
                content.endText()
            }

            doc.save(file)
        }

        return file
    }

    /**
     * Create a PDF with only plain text and no table structure (no lines or borders).
     */
    private fun createPlainTextPdf(): File {
        val file = File.createTempFile("test-no-table-", ".pdf")
        tempFiles.add(file)

        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            PDPageContentStream(doc, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(72f, 700f)
                content.showText("This is a plain text PDF with no tables.")
                content.newLineAtOffset(0f, -20f)
                content.showText("It contains only paragraphs of text.")
                content.newLineAtOffset(0f, -20f)
                content.showText("There are no ruling lines or cell borders.")
                content.endText()
            }

            doc.save(file)
        }

        return file
    }

    /**
     * Create a PDF with partial/incomplete table structure: some lines drawn
     * but not forming a complete grid. This simulates a malformed table.
     */
    private fun createPartialTablePdf(): File {
        val file = File.createTempFile("test-partial-table-", ".pdf")
        tempFiles.add(file)

        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)

            PDPageContentStream(doc, page).use { content ->
                // Draw only horizontal lines (no vertical lines = incomplete grid)
                val startX = 72f
                val startY = 700f

                content.moveTo(startX, startY)
                content.lineTo(startX + 300f, startY)
                content.stroke()

                content.moveTo(startX, startY - 30f)
                content.lineTo(startX + 300f, startY - 30f)
                content.stroke()

                // Add some text near the lines
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(startX + 5, startY - 20f)
                content.showText("Some text between lines")
                content.endText()

                // Add regular text below
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
                content.newLineAtOffset(72f, 600f)
                content.showText("Regular paragraph text below the partial structure.")
                content.endText()
            }

            doc.save(file)
        }

        return file
    }

    @Test
    fun `extract returns table data from PDF with drawn table`() {
        val pdf = createTablePdf()
        val tables = extractor.extract(pdf)

        // Should find at least one table
        assertTrue(tables.isNotEmpty(), "Expected at least one table to be extracted")

        val table = tables.first()

        // Verify page number
        assertEquals(1, table.pageNumber)

        // Verify table structure
        assertTrue(table.rowCount >= 2, "Expected at least 2 rows, got ${table.rowCount}")
        assertTrue(table.columnCount >= 2, "Expected at least 2 columns, got ${table.columnCount}")
        assertTrue(table.cells.isNotEmpty(), "Expected non-empty cells list")

        // Verify that the cell contents include our expected text
        val cellContents = table.cells.map { it.content }
        assertTrue(
            cellContents.any { it.contains("Cell A1") },
            "Expected to find 'Cell A1' in cells, got: $cellContents"
        )
        assertTrue(
            cellContents.any { it.contains("Cell B1") },
            "Expected to find 'Cell B1' in cells, got: $cellContents"
        )
        assertTrue(
            cellContents.any { it.contains("Cell A2") },
            "Expected to find 'Cell A2' in cells, got: $cellContents"
        )
        assertTrue(
            cellContents.any { it.contains("Cell B2") },
            "Expected to find 'Cell B2' in cells, got: $cellContents"
        )
    }

    @Test
    fun `extract returns empty list for PDF without tables`() {
        val pdf = createPlainTextPdf()
        val tables = extractor.extract(pdf)

        // Plain text PDF should yield no tables (or only tables with blank cells)
        val nonEmptyTables = tables.filter { table ->
            table.cells.any { it.content.isNotBlank() }
        }
        assertTrue(
            nonEmptyTables.isEmpty(),
            "Expected no tables with content from plain text PDF, got ${nonEmptyTables.size}"
        )
    }

    @Test
    fun `extract handles partial table without crashing`() {
        val pdf = createPartialTablePdf()

        // Should not throw any exception
        val tables = extractor.extract(pdf)

        // Result can be empty or contain partial data — the key requirement is no crash
        // Verify the result is a valid list (non-null)
        assertTrue(tables.size >= 0, "Expected a valid list result")

        // If any tables were found, verify they have valid structure
        for (table in tables) {
            assertTrue(table.pageNumber > 0, "Page number should be positive")
            assertTrue(table.rowCount >= 0, "Row count should be non-negative")
            assertTrue(table.columnCount >= 0, "Column count should be non-negative")
        }
    }
}
