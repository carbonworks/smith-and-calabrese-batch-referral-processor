package tech.carbonworks.snc.batchreferralparser.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [PdfTextExtractor].
 *
 * All test PDFs are created programmatically with PDFBox so no external files
 * are needed. Temp files are cleaned up after each test.
 */
class PdfTextExtractorTest {

    private val extractor = PdfTextExtractor()
    private val tempFiles = mutableListOf<File>()

    @AfterEach
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // -- Helpers ----------------------------------------------------------

    /**
     * Create a single-page PDF with the given text drawn at a fixed position.
     */
    private fun createTestPdf(text: String): File {
        val file = File.createTempFile("test-", ".pdf")
        tempFiles.add(file)
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(72f, 700f)
                content.showText(text)
                content.endText()
            }
            doc.save(file)
        }
        return file
    }

    /**
     * Create a multi-page PDF. Each entry in [pageTexts] becomes a separate page.
     * Null entries produce an empty page (no text drawn).
     */
    private fun createMultiPagePdf(pageTexts: List<String?>): File {
        val file = File.createTempFile("test-multipage-", ".pdf")
        tempFiles.add(file)
        PDDocument().use { doc ->
            for (text in pageTexts) {
                val page = PDPage()
                doc.addPage(page)
                if (text != null) {
                    PDPageContentStream(doc, page).use { content ->
                        content.beginText()
                        content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                        content.newLineAtOffset(72f, 700f)
                        content.showText(text)
                        content.endText()
                    }
                }
            }
            doc.save(file)
        }
        return file
    }

    /**
     * Create a PDF that is encrypted with a non-empty user password,
     * so opening it without the password raises InvalidPasswordException.
     */
    private fun createEncryptedPdf(): File {
        val file = File.createTempFile("test-encrypted-", ".pdf")
        tempFiles.add(file)
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(72f, 700f)
                content.showText("Secret content")
                content.endText()
            }
            val permissions = AccessPermission()
            val policy = StandardProtectionPolicy("owner-password", "user-password", permissions)
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(file)
        }
        return file
    }

    // -- Tests ------------------------------------------------------------

    @Test
    fun `extract normal single-page PDF returns text content`() {
        val expectedText = "Hello World"
        val file = createTestPdf(expectedText)

        val result = extractor.extract(file)

        assertIs<ExtractionResult.Success>(result)
        assertEquals(1, result.pages.size)

        val page = result.pages[0]
        assertEquals(1, page.pageNumber)
        assertTrue(page.hasText, "Page should have text")
        assertTrue(page.textBlocks.isNotEmpty(), "Page should contain text blocks")

        // The extracted text, when all blocks are concatenated, should contain
        // our input text. PDFBox may split "Hello World" into separate blocks
        // at the space, so we check the joined result.
        val allText = page.textBlocks.joinToString(" ") { it.text }
        assertTrue(
            allText.contains("Hello") && allText.contains("World"),
            "Extracted text should contain 'Hello' and 'World', got: $allText",
        )

        // Bounding boxes should have positive dimensions
        for (block in page.textBlocks) {
            assertTrue(block.boundingBox.width > 0, "Block width should be positive")
            assertTrue(block.boundingBox.height > 0, "Block height should be positive")
            assertTrue(block.fontSize > 0, "Font size should be positive")
        }
    }

    @Test
    fun `extract multi-page PDF returns all pages with correct page numbers`() {
        val pageTexts = listOf("Page one content", "Page two content", "Page three content")
        val file = createMultiPagePdf(pageTexts)

        val result = extractor.extract(file)

        assertIs<ExtractionResult.Success>(result)
        assertEquals(3, result.pages.size, "Should have 3 pages")

        for (i in 0 until 3) {
            val page = result.pages[i]
            assertEquals(i + 1, page.pageNumber, "Page number should be ${i + 1}")
            assertTrue(page.hasText, "Page ${i + 1} should have text")

            val pageText = page.textBlocks.joinToString(" ") { it.text }
            val expectedWord = when (i) {
                0 -> "one"
                1 -> "two"
                2 -> "three"
                else -> throw IllegalStateException()
            }
            assertTrue(
                pageText.contains(expectedWord),
                "Page ${i + 1} should contain '$expectedWord', got: $pageText",
            )
        }
    }

    @Test
    fun `extract PDF with empty page detects hasText as false`() {
        // Create a 2-page PDF: page 1 has text, page 2 is empty
        val file = createMultiPagePdf(listOf("Content on page one", null))

        val result = extractor.extract(file)

        assertIs<ExtractionResult.Success>(result)
        assertEquals(2, result.pages.size)

        val page1 = result.pages[0]
        assertTrue(page1.hasText, "Page 1 should have text")
        assertTrue(page1.textBlocks.isNotEmpty(), "Page 1 should have text blocks")

        val page2 = result.pages[1]
        assertFalse(page2.hasText, "Page 2 (empty) should have hasText=false")
        assertTrue(page2.textBlocks.isEmpty(), "Page 2 should have no text blocks")
    }

    @Test
    fun `extract corrupt file returns Error`() {
        val file = File.createTempFile("test-corrupt-", ".pdf")
        tempFiles.add(file)
        // Write garbage bytes — not a valid PDF
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte(), 0xFE.toByte()))

        val result = extractor.extract(file)

        assertIs<ExtractionResult.Error>(result)
        assertTrue(
            result.message.contains("corrupt", ignoreCase = true) ||
                result.message.contains("Failed to read", ignoreCase = true),
            "Error message should indicate corruption, got: ${result.message}",
        )
        assertEquals(file.name, result.sourceFile)
    }

    @Test
    fun `extract encrypted PDF returns Error with appropriate message`() {
        val file = createEncryptedPdf()

        val result = extractor.extract(file)

        assertIs<ExtractionResult.Error>(result)
        assertTrue(
            result.message.contains("encrypt", ignoreCase = true) ||
                result.message.contains("password", ignoreCase = true),
            "Error message should mention encryption or password, got: ${result.message}",
        )
        assertEquals(file.name, result.sourceFile)
    }
}
