package tech.carbonworks.snc.batchreferralparser.logging

import java.io.OutputStream

/**
 * An [OutputStream] that duplicates all writes to two underlying streams.
 *
 * This is used to redirect [System.out] and [System.err] so that existing
 * `println()` calls continue to appear on the console while also being
 * captured in the log file.
 *
 * Neither stream is closed when [close] is called — the caller is
 * responsible for the lifecycle of both streams.
 */
class TeeOutputStream(
    private val primary: OutputStream,
    private val secondary: OutputStream,
) : OutputStream() {

    override fun write(b: Int) {
        primary.write(b)
        secondary.write(b)
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        primary.write(b, off, len)
        secondary.write(b, off, len)
    }

    override fun flush() {
        primary.flush()
        secondary.flush()
    }

    override fun close() {
        // Intentionally do not close either stream.
        // The caller owns their lifecycle.
        flush()
    }
}
