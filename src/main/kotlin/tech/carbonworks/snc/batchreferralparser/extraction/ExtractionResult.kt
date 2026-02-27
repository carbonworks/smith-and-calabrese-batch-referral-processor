package tech.carbonworks.snc.batchreferralparser.extraction

/**
 * Bounding box representing a rectangular region on a PDF page.
 * Coordinates are in PDF user-space units (1/72 inch), with origin at the
 * bottom-left of the page (PDF standard) or top-left depending on the
 * extraction method. PDFBox's TextPosition uses top-left origin.
 */
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    /**
     * Check whether this bounding box intersects another region.
     */
    fun intersects(other: BoundingBox): Boolean {
        return x < other.x + other.width &&
            x + width > other.x &&
            y < other.y + other.height &&
            y + height > other.y
    }

    /**
     * Check whether this bounding box is fully contained within another region.
     */
    fun isContainedIn(other: BoundingBox): Boolean {
        return x >= other.x &&
            y >= other.y &&
            x + width <= other.x + other.width &&
            y + height <= other.y + other.height
    }
}

/**
 * A contiguous block of text extracted from a PDF page.
 * Represents one or more characters grouped by spatial proximity on the same line.
 */
data class TextBlock(
    val text: String,
    val pageNumber: Int,
    val boundingBox: BoundingBox,
    val fontSize: Float,
)

/**
 * Metadata and extracted content for a single PDF page.
 */
data class PageInfo(
    val pageNumber: Int,
    val width: Float,
    val height: Float,
    val hasText: Boolean,
    val textBlocks: List<TextBlock>,
    val strippedText: String = "",
)

/**
 * Result of a PDF text extraction operation.
 * Uses a sealed class hierarchy so callers must handle both success and error cases.
 */
sealed class ExtractionResult {
    /**
     * Successful extraction containing page data with text blocks.
     */
    data class Success(
        val pages: List<PageInfo>,
        val sourceFile: String,
    ) : ExtractionResult()

    /**
     * Failed extraction with an error message and optional cause.
     */
    data class Error(
        val message: String,
        val sourceFile: String,
        val cause: Throwable? = null,
    ) : ExtractionResult()
}
