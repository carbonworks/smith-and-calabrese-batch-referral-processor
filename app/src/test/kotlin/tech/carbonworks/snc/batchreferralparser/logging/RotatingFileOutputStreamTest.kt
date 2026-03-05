package tech.carbonworks.snc.batchreferralparser.logging

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [RotatingFileOutputStream].
 */
class RotatingFileOutputStreamTest {

    @TempDir
    lateinit var tempDir: File

    // ------------------------------------------------------------------
    // Test 1: Writes appear in the log file
    // ------------------------------------------------------------------

    @Test
    fun `writes appear in the log file`() {
        val logFile = File(tempDir, "app.log")
        val stream = RotatingFileOutputStream(logFile, maxBytes = 1024)

        stream.write("Hello, log!\n".toByteArray())
        stream.flush()

        assertTrue(logFile.exists(), "Log file should exist after writing")
        assertEquals("Hello, log!\n", logFile.readText())

        stream.close()
    }

    // ------------------------------------------------------------------
    // Test 2: Rotation occurs when file exceeds max size
    // ------------------------------------------------------------------

    @Test
    fun `rotation occurs when file exceeds max size`() {
        val logFile = File(tempDir, "app.log")
        // Very small max to trigger rotation quickly
        val stream = RotatingFileOutputStream(logFile, maxBytes = 50, maxRotated = 2)

        // Write enough to trigger rotation
        val chunk = "A".repeat(60) + "\n" // 61 bytes, exceeds 50
        stream.write(chunk.toByteArray())
        stream.flush()

        // After rotation: app.log should be empty (new file), app.1.log should have the data
        val rotated1 = File(tempDir, "app.1.log")
        assertTrue(rotated1.exists(), "app.1.log should exist after rotation")
        assertTrue(logFile.exists(), "app.log should exist (new empty file)")
        assertEquals(0L, logFile.length(), "app.log should be empty after rotation")

        stream.close()
    }

    // ------------------------------------------------------------------
    // Test 3: Multiple rotations shift files correctly
    // ------------------------------------------------------------------

    @Test
    fun `multiple rotations shift files correctly`() {
        val logFile = File(tempDir, "app.log")
        val stream = RotatingFileOutputStream(logFile, maxBytes = 20, maxRotated = 2)

        // Write chunk 1 — triggers first rotation
        stream.write("AAAAAAAAAAAAAAAAAAAAAAA\n".toByteArray()) // 24 bytes > 20
        stream.flush()

        // Write chunk 2 — triggers second rotation
        stream.write("BBBBBBBBBBBBBBBBBBBBBBB\n".toByteArray()) // 24 bytes > 20
        stream.flush()

        val rotated1 = File(tempDir, "app.1.log")
        val rotated2 = File(tempDir, "app.2.log")

        assertTrue(rotated1.exists(), "app.1.log should exist")
        assertTrue(rotated2.exists(), "app.2.log should exist")

        // app.2.log has the oldest data (chunk 1), app.1.log has chunk 2
        assertTrue(rotated2.readText().startsWith("A"), "app.2.log should contain chunk 1")
        assertTrue(rotated1.readText().startsWith("B"), "app.1.log should contain chunk 2")

        stream.close()
    }

    // ------------------------------------------------------------------
    // Test 4: Old rotated files are deleted beyond maxRotated
    // ------------------------------------------------------------------

    @Test
    fun `old rotated files are deleted beyond maxRotated`() {
        val logFile = File(tempDir, "app.log")
        val stream = RotatingFileOutputStream(logFile, maxBytes = 20, maxRotated = 2)

        // Write 3 chunks to trigger 3 rotations
        repeat(3) { i ->
            val data = "${'A' + i}".repeat(25) + "\n"
            stream.write(data.toByteArray())
            stream.flush()
        }

        val rotated1 = File(tempDir, "app.1.log")
        val rotated2 = File(tempDir, "app.2.log")
        val rotated3 = File(tempDir, "app.3.log")

        assertTrue(rotated1.exists(), "app.1.log should exist")
        assertTrue(rotated2.exists(), "app.2.log should exist")
        assertFalse(rotated3.exists(), "app.3.log should NOT exist (maxRotated=2)")

        stream.close()
    }

    // ------------------------------------------------------------------
    // Test 5: Appending to existing file resumes byte count
    // ------------------------------------------------------------------

    @Test
    fun `appending to existing file resumes byte count`() {
        val logFile = File(tempDir, "app.log")

        // Pre-populate with 15 bytes
        logFile.writeText("existing content")

        // Open with max 20 bytes — only 4-5 bytes of headroom
        val stream = RotatingFileOutputStream(logFile, maxBytes = 20, maxRotated = 2)

        // Write 10 bytes — should trigger rotation (15 + 10 = 25 > 20)
        stream.write("extra data".toByteArray())
        stream.flush()

        val rotated1 = File(tempDir, "app.1.log")
        assertTrue(rotated1.exists(), "Rotation should trigger based on accumulated size")

        stream.close()
    }

    // ------------------------------------------------------------------
    // Test 6: logDirectory returns the parent directory
    // ------------------------------------------------------------------

    @Test
    fun `logDirectory returns the parent directory`() {
        val logFile = File(tempDir, "app.log")
        val stream = RotatingFileOutputStream(logFile, maxBytes = 1024)

        assertEquals(tempDir.absolutePath, stream.logDirectory().absolutePath)

        stream.close()
    }
}
