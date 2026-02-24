package tech.carbonworks.snc.batchreferralparser.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException

/**
 * Extracts text with positional information from PDF files using Apache PDFBox 3.x.
 *
 * Text is grouped into [TextBlock] entries — contiguous runs of characters on the
 * same line, separated by whitespace gaps. Each block carries its bounding box,
 * page number, and dominant font size.
 *
 * Usage:
 * ```kotlin
 * val extractor = PdfTextExtractor()
 * when (val result = extractor.extract(File("referral.pdf"))) {
 *     is ExtractionResult.Success -> result.pages.forEach { page ->
 *         page.textBlocks.forEach { block -> println(block.text) }
 *     }
 *     is ExtractionResult.Error -> System.err.println(result.message)
 * }
 * ```
 */
class PdfTextExtractor {

    /**
     * Horizontal gap (in PDF user-space units) between characters that triggers
     * a new text block on the same line. Characters separated by less than this
     * threshold are merged into one block.
     */
    private val wordGapThreshold = 3.0f

    /**
     * Vertical tolerance (in PDF user-space units) for considering two characters
     * to be on the same line. Characters whose Y coordinates differ by less than
     * this value are treated as same-line.
     */
    private val lineYTolerance = 2.0f

    /**
     * Extract all text with positional information from every page of the given PDF.
     *
     * @param file the PDF file to extract from
     * @return [ExtractionResult.Success] with page data, or [ExtractionResult.Error]
     *         if the file cannot be read (encrypted, corrupt, missing, etc.)
     */
    fun extract(file: File): ExtractionResult {
        if (!file.exists()) {
            return ExtractionResult.Error(
                message = "File not found: ${file.absolutePath}",
                sourceFile = file.name,
            )
        }

        return try {
            Loader.loadPDF(file).use { document ->
                extractFromDocument(document, file.name)
            }
        } catch (e: InvalidPasswordException) {
            ExtractionResult.Error(
                message = "PDF is encrypted or password-protected: ${file.name}",
                sourceFile = file.name,
                cause = e,
            )
        } catch (e: IOException) {
            ExtractionResult.Error(
                message = "Failed to read PDF (file may be corrupt): ${file.name} — ${e.message}",
                sourceFile = file.name,
                cause = e,
            )
        } catch (e: Exception) {
            ExtractionResult.Error(
                message = "Unexpected error processing PDF: ${file.name} — ${e.message}",
                sourceFile = file.name,
                cause = e,
            )
        }
    }

    /**
     * Extract text from a specific rectangular region on a specific page.
     *
     * Only text blocks whose bounding boxes intersect the given [region] are
     * included in the result. This is useful for coordinate-based field extraction
     * where the PDF layout is known.
     *
     * @param file the PDF file to extract from
     * @param pageNumber 1-based page number to extract from
     * @param region bounding box defining the extraction region
     * @return [ExtractionResult.Success] containing only matching text blocks,
     *         or [ExtractionResult.Error] on failure
     */
    fun extractFromRegion(file: File, pageNumber: Int, region: BoundingBox): ExtractionResult {
        val fullResult = extract(file)

        if (fullResult is ExtractionResult.Error) {
            return fullResult
        }

        val success = fullResult as ExtractionResult.Success
        val targetPage = success.pages.find { it.pageNumber == pageNumber }
            ?: return ExtractionResult.Error(
                message = "Page $pageNumber not found in ${file.name} (document has ${success.pages.size} pages)",
                sourceFile = file.name,
            )

        val filteredBlocks = targetPage.textBlocks.filter { block ->
            block.boundingBox.intersects(region)
        }

        val filteredPage = targetPage.copy(
            hasText = filteredBlocks.isNotEmpty(),
            textBlocks = filteredBlocks,
        )

        return ExtractionResult.Success(
            pages = listOf(filteredPage),
            sourceFile = success.sourceFile,
        )
    }

    /**
     * Extract text from an already-loaded PDDocument.
     */
    private fun extractFromDocument(document: PDDocument, sourceFileName: String): ExtractionResult {
        val pageCount = document.numberOfPages
        if (pageCount == 0) {
            return ExtractionResult.Success(
                pages = emptyList(),
                sourceFile = sourceFileName,
            )
        }

        val pages = mutableListOf<PageInfo>()

        for (pageIndex in 0 until pageCount) {
            val pageNumber = pageIndex + 1
            val pdPage = document.getPage(pageIndex)
            val mediaBox = pdPage.mediaBox

            val collector = PositionCollectingStripper()
            collector.startPage = pageNumber
            collector.endPage = pageNumber

            collector.getText(document)
            val positions = collector.collectedPositions

            val textBlocks = groupIntoTextBlocks(positions, pageNumber)

            pages.add(
                PageInfo(
                    pageNumber = pageNumber,
                    width = mediaBox.width,
                    height = mediaBox.height,
                    hasText = textBlocks.isNotEmpty(),
                    textBlocks = textBlocks,
                )
            )
        }

        return ExtractionResult.Success(
            pages = pages,
            sourceFile = sourceFileName,
        )
    }

    /**
     * Group individual character positions into text blocks.
     *
     * Characters are grouped into a block when they are on the same line (within
     * [lineYTolerance]) and horizontally adjacent (gap less than [wordGapThreshold]).
     * A gap larger than the threshold starts a new block.
     */
    private fun groupIntoTextBlocks(
        positions: List<TextPosition>,
        pageNumber: Int,
    ): List<TextBlock> {
        if (positions.isEmpty()) return emptyList()

        // Sort by Y (top to bottom), then X (left to right)
        val sorted = positions.sortedWith(compareBy({ it.yDirAdj }, { it.xDirAdj }))

        val blocks = mutableListOf<TextBlock>()
        var currentChars = mutableListOf(sorted[0])

        for (i in 1 until sorted.size) {
            val current = sorted[i]
            val previous = sorted[i - 1]

            val sameLine = Math.abs(current.yDirAdj - previous.yDirAdj) < lineYTolerance
            val horizontalGap = current.xDirAdj - (previous.xDirAdj + previous.width)
            val adjacent = horizontalGap < wordGapThreshold

            if (sameLine && adjacent) {
                currentChars.add(current)
            } else {
                blocks.add(buildTextBlock(currentChars, pageNumber))
                currentChars = mutableListOf(current)
            }
        }

        // Flush the last accumulated block
        if (currentChars.isNotEmpty()) {
            blocks.add(buildTextBlock(currentChars, pageNumber))
        }

        return blocks
    }

    /**
     * Build a [TextBlock] from a list of consecutive [TextPosition] characters.
     */
    private fun buildTextBlock(chars: List<TextPosition>, pageNumber: Int): TextBlock {
        val text = chars.joinToString("") { it.unicode }

        val minX = chars.minOf { it.xDirAdj }
        val minY = chars.minOf { it.yDirAdj - it.height }
        val maxX = chars.maxOf { it.xDirAdj + it.width }
        val maxY = chars.maxOf { it.yDirAdj }

        // Use the most common (modal) font size in the block
        val fontSize = chars.groupBy { it.fontSize }
            .maxByOrNull { it.value.size }
            ?.key ?: chars.first().fontSize

        return TextBlock(
            text = text,
            pageNumber = pageNumber,
            boundingBox = BoundingBox(
                x = minX,
                y = minY,
                width = maxX - minX,
                height = maxY - minY,
            ),
            fontSize = fontSize,
        )
    }

    /**
     * Custom PDFTextStripper subclass that collects TextPosition objects
     * for each character, giving us access to coordinates, font sizes, and
     * individual character data.
     */
    private class PositionCollectingStripper : PDFTextStripper() {

        val collectedPositions = mutableListOf<TextPosition>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (position in textPositions) {
                // Skip whitespace-only characters — we group words by gaps instead
                if (position.unicode.isNotBlank()) {
                    collectedPositions.add(position)
                }
            }
        }
    }
}

/**
 * Type alias for PDFBox's InvalidPasswordException to keep imports clean.
 * PDFBox 3.x uses this exception for encrypted/password-protected PDFs.
 */
private typealias InvalidPasswordException = org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
