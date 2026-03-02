package tech.carbonworks.snc.batchreferralparser.util

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.output.SpreadsheetWriter
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [PhiMask] and [PhiPreferences] covering masking logic,
 * preference persistence, and the SpreadsheetWriter boundary guarantee
 * that raw (unmasked) values are always written to XLSX output.
 *
 * Uses save/restore helpers to isolate singleton and preference state
 * between tests.
 */
class PhiMaskTest {

    @TempDir
    lateinit var tempDir: File

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Run [block] with [PhiMask.maskingEnabled] set to [enabled], restoring afterward. */
    private fun <T> withMasking(enabled: Boolean, block: () -> T): T {
        val original = PhiMask.maskingEnabled
        PhiMask.maskingEnabled = enabled
        return try {
            block()
        } finally {
            PhiMask.maskingEnabled = original
        }
    }

    /** Run [block] with the showPhiByDefault preference set to [value], restoring afterward. */
    private fun <T> withShowByDefault(value: Boolean, block: () -> T): T {
        val original = PhiPreferences.getShowByDefault()
        PhiPreferences.setShowByDefault(value)
        return try {
            block()
        } finally {
            PhiPreferences.setShowByDefault(original)
        }
    }

    /** Run [block] with the toggleDismissed preference set to [value], restoring afterward. */
    private fun <T> withToggleDismissed(value: Boolean, block: () -> T): T {
        val original = PhiPreferences.getToggleDismissed()
        PhiPreferences.setToggleDismissed(value)
        return try {
            block()
        } finally {
            PhiPreferences.setToggleDismissed(original)
        }
    }

    /** Open an .xlsx file as an [XSSFWorkbook]. */
    private fun openWorkbook(file: File): XSSFWorkbook {
        return FileInputStream(file).use { XSSFWorkbook(it) }
    }

    /** Read a cell as a string; returns empty string for blank/missing cells. */
    private fun cellText(workbook: XSSFWorkbook, row: Int, col: Int): String {
        val sheet = workbook.getSheetAt(0)
        val r = sheet.getRow(row) ?: return ""
        val c = r.getCell(col) ?: return ""
        return c.stringCellValue
    }

    private val fixedTimestamp = LocalDateTime.of(2026, 3, 2, 10, 0, 0)

    // -------------------------------------------------------------------
    // PhiMask.maskValue
    // -------------------------------------------------------------------

    @Test
    fun `maskValue - null input returns null`() = withMasking(enabled = true) {
        assertNull(PhiMask.maskValue(null))
    }

    @Test
    fun `maskValue - empty string returns empty`() = withMasking(enabled = true) {
        assertEquals("", PhiMask.maskValue(""))
    }

    @Test
    fun `maskValue - single word masked`() = withMasking(enabled = true) {
        assertEquals("J***", PhiMask.maskValue("Jane"))
    }

    @Test
    fun `maskValue - multi-word masked`() = withMasking(enabled = true) {
        assertEquals("1** M*** S*****", PhiMask.maskValue("123 Main Street"))
    }

    @Test
    fun `maskValue - single-char word becomes asterisk`() = withMasking(enabled = true) {
        assertEquals("* * D**", PhiMask.maskValue("J A Doe"))
    }

    @Test
    fun `maskValue - blank string returns unchanged`() = withMasking(enabled = true) {
        assertEquals("   ", PhiMask.maskValue("   "))
    }

    @Test
    fun `maskValue - masking disabled passes through`() = withMasking(enabled = false) {
        assertEquals("Jane Doe", PhiMask.maskValue("Jane Doe"))
    }

    @Test
    fun `maskValue - masking disabled with null returns null`() = withMasking(enabled = false) {
        assertNull(PhiMask.maskValue(null))
    }

    // -------------------------------------------------------------------
    // PhiMask.maskDisplay
    // -------------------------------------------------------------------

    @Test
    fun `maskDisplay - empty string returns empty`() = withMasking(enabled = true) {
        assertEquals("", PhiMask.maskDisplay(""))
    }

    @Test
    fun `maskDisplay - string is masked`() = withMasking(enabled = true) {
        assertEquals("0*********", PhiMask.maskDisplay("05/15/1990"))
    }

    @Test
    fun `maskDisplay - masking disabled passes through`() = withMasking(enabled = false) {
        assertEquals("05/15/1990", PhiMask.maskDisplay("05/15/1990"))
    }

    // -------------------------------------------------------------------
    // Integration
    // -------------------------------------------------------------------

    @Test
    fun `toggling maskingEnabled changes maskDisplay output`() {
        val original = PhiMask.maskingEnabled
        try {
            PhiMask.maskingEnabled = true
            assertEquals("J***", PhiMask.maskDisplay("Jane"))

            PhiMask.maskingEnabled = false
            assertEquals("Jane", PhiMask.maskDisplay("Jane"))
        } finally {
            PhiMask.maskingEnabled = original
        }
    }

    // -------------------------------------------------------------------
    // PhiPreferences
    // -------------------------------------------------------------------

    @Test
    fun `showPhiByDefault defaults to false`() = withShowByDefault(false) {
        assertFalse(PhiPreferences.getShowByDefault())
    }

    @Test
    fun `setShowByDefault round-trips`() = withShowByDefault(true) {
        assertTrue(PhiPreferences.getShowByDefault())
    }

    @Test
    fun `phiToggleDismissed defaults to false`() = withToggleDismissed(false) {
        assertFalse(PhiPreferences.getToggleDismissed())
    }

    @Test
    fun `setToggleDismissed round-trips`() = withToggleDismissed(true) {
        assertTrue(PhiPreferences.getToggleDismissed())
    }

    // -------------------------------------------------------------------
    // SpreadsheetWriter boundary
    // -------------------------------------------------------------------

    @Test
    fun `SpreadsheetWriter writes unmasked values when masking is enabled`() = withMasking(enabled = true) {
        assertTrue(PhiMask.isMaskingEnabled(), "Masking should be enabled for this test")

        val referral = ReferralFields(
            firstName = "Jane",
            lastName = "Doe",
            streetAddress = "123 Main St",
        )
        val file = SpreadsheetWriter.write(listOf(referral), tempDir, fixedTimestamp)

        openWorkbook(file).use { wb ->
            // Row 1 = first data row; col 0 = First Name, col 2 = Last Name, col 11 = Street Address
            assertEquals("Jane", cellText(wb, 1, 0))
            assertEquals("Doe", cellText(wb, 1, 2))
            assertEquals("123 Main St", cellText(wb, 1, 11))
        }
    }
}
