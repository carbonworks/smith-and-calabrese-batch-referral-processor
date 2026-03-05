package tech.carbonworks.snc.batchreferralparser.logging

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

/**
 * Tests for [TeeOutputStream].
 */
class TeeOutputStreamTest {

    // ------------------------------------------------------------------
    // Test 1: Single-byte writes go to both streams
    // ------------------------------------------------------------------

    @Test
    fun `single-byte writes go to both streams`() {
        val primary = ByteArrayOutputStream()
        val secondary = ByteArrayOutputStream()
        val tee = TeeOutputStream(primary, secondary)

        tee.write('A'.code)
        tee.write('B'.code)
        tee.flush()

        assertEquals("AB", primary.toString())
        assertEquals("AB", secondary.toString())
    }

    // ------------------------------------------------------------------
    // Test 2: Byte array writes go to both streams
    // ------------------------------------------------------------------

    @Test
    fun `byte array writes go to both streams`() {
        val primary = ByteArrayOutputStream()
        val secondary = ByteArrayOutputStream()
        val tee = TeeOutputStream(primary, secondary)

        tee.write("Hello, world!".toByteArray())
        tee.flush()

        assertEquals("Hello, world!", primary.toString())
        assertEquals("Hello, world!", secondary.toString())
    }

    // ------------------------------------------------------------------
    // Test 3: Partial byte array writes go to both streams
    // ------------------------------------------------------------------

    @Test
    fun `partial byte array writes go to both streams`() {
        val primary = ByteArrayOutputStream()
        val secondary = ByteArrayOutputStream()
        val tee = TeeOutputStream(primary, secondary)

        val data = "Hello, world!".toByteArray()
        tee.write(data, 7, 5) // "world"
        tee.flush()

        assertEquals("world", primary.toString())
        assertEquals("world", secondary.toString())
    }

    // ------------------------------------------------------------------
    // Test 4: Close does not close underlying streams
    // ------------------------------------------------------------------

    @Test
    fun `close flushes but does not close underlying streams`() {
        val primary = ByteArrayOutputStream()
        val secondary = ByteArrayOutputStream()
        val tee = TeeOutputStream(primary, secondary)

        tee.write("data".toByteArray())
        tee.close()

        // Underlying streams should still be writable after tee is closed
        primary.write("more".toByteArray())
        assertEquals("datamore", primary.toString())
    }
}
