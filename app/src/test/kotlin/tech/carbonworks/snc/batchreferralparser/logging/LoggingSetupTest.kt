package tech.carbonworks.snc.batchreferralparser.logging

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [LoggingSetup].
 */
class LoggingSetupTest {

    // ------------------------------------------------------------------
    // Test 1: resolveLogDirectory returns a platform-appropriate path
    // ------------------------------------------------------------------

    @Test
    fun `resolveLogDirectory returns a non-null path`() {
        val dir = LoggingSetup.resolveLogDirectory()
        assertNotNull(dir, "Log directory should not be null")
        assertTrue(dir.path.isNotBlank(), "Log directory path should not be blank")
    }

    // ------------------------------------------------------------------
    // Test 2: resolveLogDirectory contains expected path components
    // ------------------------------------------------------------------

    @Test
    fun `resolveLogDirectory contains CarbonWorks and BatchAuthProcessor`() {
        val dir = LoggingSetup.resolveLogDirectory()
        val path = dir.path.replace("\\", "/")

        assertTrue(
            path.contains("CarbonWorks") && path.contains("BatchAuthProcessor"),
            "Log directory should contain CarbonWorks and BatchAuthProcessor: $path",
        )
    }

    // ------------------------------------------------------------------
    // Test 3: resolveLogDirectory uses correct OS-specific base on Windows
    // ------------------------------------------------------------------

    @Test
    fun `resolveLogDirectory uses logs subdirectory on Windows`() {
        val osName = System.getProperty("os.name", "").lowercase()
        if (!osName.contains("win")) return // skip on non-Windows

        val dir = LoggingSetup.resolveLogDirectory()
        val path = dir.path.replace("\\", "/")

        assertTrue(
            path.endsWith("/logs"),
            "Windows log directory should end with /logs: $path",
        )
    }
}
