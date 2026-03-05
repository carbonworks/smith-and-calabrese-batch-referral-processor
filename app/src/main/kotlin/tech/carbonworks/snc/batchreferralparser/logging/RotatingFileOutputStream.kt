package tech.carbonworks.snc.batchreferralparser.logging

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * An [OutputStream] that writes to a file and rotates when the file exceeds
 * [maxBytes]. Rotation shifts existing log files:
 *
 *     app.log   -> app.1.log
 *     app.1.log -> app.2.log
 *     app.2.log -> (deleted)
 *
 * At most [maxRotated] rotated files are kept (default 2). The current log
 * file is always named [logFile].
 *
 * Thread safety: all public write methods are synchronized on this instance.
 */
class RotatingFileOutputStream(
    private val logFile: File,
    private val maxBytes: Long = 5L * 1024 * 1024, // 5 MB
    private val maxRotated: Int = 2,
) : OutputStream() {

    private var bytesWritten: Long = 0L
    private var fileOut: FileOutputStream

    init {
        logFile.parentFile?.mkdirs()
        bytesWritten = if (logFile.exists()) logFile.length() else 0L
        fileOut = FileOutputStream(logFile, true) // append
    }

    @Synchronized
    override fun write(b: Int) {
        fileOut.write(b)
        bytesWritten++
        rotateIfNeeded()
    }

    @Synchronized
    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        fileOut.write(b, off, len)
        bytesWritten += len
        rotateIfNeeded()
    }

    @Synchronized
    override fun flush() {
        fileOut.flush()
    }

    @Synchronized
    override fun close() {
        fileOut.close()
    }

    /**
     * Returns the directory containing the log files.
     */
    fun logDirectory(): File = logFile.parentFile

    /**
     * Returns the path of the current log file.
     */
    fun logFilePath(): File = logFile

    // ------------------------------------------------------------------
    // Rotation
    // ------------------------------------------------------------------

    private fun rotateIfNeeded() {
        if (bytesWritten < maxBytes) return

        fileOut.close()

        // Shift existing rotated files: app.2.log deleted, app.1.log -> app.2.log, etc.
        val baseName = logFile.nameWithoutExtension // "app"
        val ext = logFile.extension               // "log"
        val dir = logFile.parentFile

        for (i in maxRotated downTo 1) {
            val src = if (i == 1) logFile else File(dir, "$baseName.${i - 1}.$ext")
            val dst = File(dir, "$baseName.$i.$ext")
            if (dst.exists()) dst.delete()
            if (src.exists()) src.renameTo(dst)
        }

        // Open a fresh log file (truncate)
        fileOut = FileOutputStream(logFile, false)
        bytesWritten = 0L
    }
}
