package tech.carbonworks.snc.batchreferralparser.logging

import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Initializes file-based logging for the application.
 *
 * Call [initialize] once, early in `main()`, before any other code runs.
 * After initialization:
 *
 * - All `System.out` output goes to both the console and the log file.
 * - All `System.err` output goes to both the console and the log file.
 * - The log file rotates at 5 MB, keeping up to 2 rotated copies.
 *
 * Log file locations follow OS conventions:
 * - Windows: `%LOCALAPPDATA%/CarbonWorks/BatchAuthProcessor/logs/`
 * - macOS:   `~/Library/Logs/CarbonWorks/BatchAuthProcessor/`
 * - Linux:   `~/.local/share/CarbonWorks/BatchAuthProcessor/logs/`
 */
object LoggingSetup {

    private const val LOG_FILE_NAME = "app.log"
    private const val MAX_LOG_BYTES = 5L * 1024 * 1024 // 5 MB
    private const val MAX_ROTATED_FILES = 2

    private var initialized = false
    private var rotatingStream: RotatingFileOutputStream? = null

    /**
     * Set up file logging. Safe to call multiple times — subsequent calls
     * are no-ops.
     */
    fun initialize() {
        if (initialized) return
        initialized = true

        try {
            val logDir = resolveLogDirectory()
            logDir.mkdirs()

            val logFile = File(logDir, LOG_FILE_NAME)
            val rotating = RotatingFileOutputStream(logFile, MAX_LOG_BYTES, MAX_ROTATED_FILES)
            rotatingStream = rotating

            // Tee stdout: console + file
            val originalOut = System.out
            val teeOut = TeeOutputStream(originalOut, rotating)
            System.setOut(PrintStream(teeOut, true))

            // Tee stderr: console + file
            val originalErr = System.err
            val teeErr = TeeOutputStream(originalErr, rotating)
            System.setErr(PrintStream(teeErr, true))

            logSessionStart()
        } catch (e: Exception) {
            // If logging setup fails, print to original stderr and continue.
            // The app should not crash because log files are inaccessible.
            System.err.println("[LoggingSetup] Failed to initialize file logging: ${e.message}")
        }
    }

    /**
     * Returns the directory where log files are stored, or null if logging
     * has not been initialized.
     */
    fun logDirectory(): File? = rotatingStream?.logDirectory()

    /**
     * Returns the path of the current log file, or null if logging has not
     * been initialized.
     */
    fun logFilePath(): File? = rotatingStream?.logFilePath()

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Resolve the platform-specific log directory.
     */
    internal fun resolveLogDirectory(): File {
        val osName = System.getProperty("os.name", "").lowercase()
        return when {
            osName.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                    ?: (System.getProperty("user.home") + "/AppData/Local")
                File(localAppData, "CarbonWorks/BatchAuthProcessor/logs")
            }
            osName.contains("mac") -> {
                val home = System.getProperty("user.home")
                File(home, "Library/Logs/CarbonWorks/BatchAuthProcessor")
            }
            else -> {
                // Linux / other Unix
                val home = System.getProperty("user.home")
                File(home, ".local/share/CarbonWorks/BatchAuthProcessor/logs")
            }
        }
    }

    /**
     * Print a session-start banner with diagnostic information.
     */
    private fun logSessionStart() {
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        )
        val osName = System.getProperty("os.name", "unknown")
        val osVersion = System.getProperty("os.version", "unknown")
        val osArch = System.getProperty("os.arch", "unknown")
        val javaVersion = System.getProperty("java.version", "unknown")
        val javaVendor = System.getProperty("java.vendor", "unknown")
        val appVersion = resolveAppVersion()

        println()
        println("================================================================")
        println("  Session started: $timestamp")
        println("  App version:     $appVersion")
        println("  OS:              $osName $osVersion ($osArch)")
        println("  Java:            $javaVersion ($javaVendor)")
        println("  Log file:        ${rotatingStream?.logFilePath()?.absolutePath}")
        println("================================================================")
        println()
    }

    /**
     * Attempt to read the application version. Falls back to "dev" if the
     * version is not available (e.g., running from IDE vs. packaged app).
     */
    private fun resolveAppVersion(): String {
        // The jpackage'd app sets this property via the launcher
        val fromPackage = System.getProperty("jpackage.app-version")
        if (!fromPackage.isNullOrBlank()) return fromPackage

        // Fallback: try Implementation-Version from manifest
        val fromManifest = LoggingSetup::class.java.`package`?.implementationVersion
        if (!fromManifest.isNullOrBlank()) return fromManifest

        return "dev"
    }
}
