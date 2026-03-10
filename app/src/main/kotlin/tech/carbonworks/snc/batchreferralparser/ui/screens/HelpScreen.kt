package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.logging.LoggingSetup
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Help & Support screen: displays usage instructions, supported formats,
 * tips, and support contact information.
 *
 * @param onBack callback to return to the main (file selection) screen
 * @param window optional AWT window reference used as parent for the save dialog
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    window: java.awt.Window? = null,
) {
    val scrollState = rememberScrollState()
    var logSaveMessage by remember { mutableStateOf<String?>(null) }
    var logSaveError by remember { mutableStateOf<String?>(null) }
    var savedLogFile by remember { mutableStateOf<File?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite)
            .padding(32.dp),
    ) {
        // Header
        Text(
            text = "Help & Support",
            style = MaterialTheme.typography.headlineSmall,
            color = DeepInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Learn how to use the Batch Authorization Processor",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scrollable content area with visible scrollbar
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Getting Started section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Getting Started")
                        HelpStep(
                            number = "1",
                            text = "Select PDF authorization files using the file picker or drag-and-drop.",
                        )
                        HelpStep(
                            number = "2",
                            text = "Click the Process button to extract data from all selected files. Each file shows a green checkmark on success or a red X if it cannot be processed. One problem file will not stop the rest of the batch.",
                        )
                        HelpStep(
                            number = "3",
                            text = "Review the extracted data in the results cards.",
                        )
                        HelpStep(
                            number = "4",
                            text = "Click \"Export\" to export the results as a spreadsheet.",
                        )
                        HelpStep(
                            number = "5",
                            text = "Click \"Start Over\" on the results screen to return to file selection for a new batch.",
                        )
                    }
                }

                // Supported Formats section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Supported Formats")
                        HelpBullet(
                            text = "The tool processes Maryland DDS authorization PDFs.",
                        )
                        HelpBullet(
                            text = "Up to 50 PDFs can be processed in a single batch.",
                        )
                        HelpBullet(
                            text = "Output is saved as an XLSX spreadsheet compatible with Excel and Google Sheets. Optionally export as CSV or TSV instead (see Tips).",
                        )
                    }
                }

                // Tips section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Tips")
                        HelpBullet(
                            text = "If fields are missing, check the Warnings section on the results screen.",
                        )
                        HelpBullet(
                            text = "Each result card shows an orange banner listing any fields that could not be extracted and may need manual review.",
                        )
                        HelpBullet(
                            text = "Both the file picker and the export save dialog remember the last directory used.",
                        )
                        HelpBullet(
                            text = "Drag and drop multiple files at once for faster batch loading.",
                        )
                        HelpBullet(
                            text = "Files added more than once are automatically skipped.",
                        )
                        HelpBullet(
                            text = "Extracted data is masked by default for privacy. Use the eye toggle on the results screen to reveal values, or change the default in Settings.",
                        )
                        HelpBullet(
                            text = "Each result card has its own eye icon to unmask just that card without revealing all others.",
                        )
                        HelpBullet(
                            text = "Click \"Open PDF\" on any result card to view the original source document.",
                        )
                        HelpBullet(
                            text = "Result cards display Provider/Doctor Name, Special Instructions, and Examiner Name & Contact when present in the authorization PDF.",
                        )
                        HelpBullet(
                            text = "Use the format dropdown on the results screen to choose XLSX, CSV, or TSV export. This preference persists across sessions.",
                        )
                        HelpBullet(
                            text = "After exporting, click the filename to open the spreadsheet or \"Open folder\" to see it in your file manager.",
                        )
                        HelpBullet(
                            text = "The gear icon on the main screen opens Settings, where you can change the default privacy masking behavior.",
                        )
                        HelpBullet(
                            text = "Triple-click on any field value in the results to select it for copying. Unmask the card first to see actual values.",
                        )
                    }
                }

                // Export Settings section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Export Settings")
                        HelpBullet(
                            text = "Click the gear icon next to the Export button on the results screen to open Export Settings.",
                        )
                        HelpBullet(
                            text = "Use column visibility toggles to choose which fields appear in the spreadsheet.",
                        )
                        HelpBullet(
                            text = "Drag columns to reorder them in the exported spreadsheet.",
                        )
                        HelpBullet(
                            text = "Use \"All Fields\" or \"Essential Only\" presets to quickly configure column visibility.",
                        )
                        HelpBullet(
                            text = "Enable \"Place each service on its own row\" to expand multi-service referrals into separate rows.",
                        )
                    }
                }

                // Support section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Support")
                        Text(
                            text = "For questions or issues, contact:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "support@carbonworks.tech",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BrandGreen,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().mail(URI("mailto:support@carbonworks.tech"))
                                    }
                                } catch (_: Exception) {
                                    // Non-critical: email client may not be configured
                                }
                            },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "If you encounter a problem, save a copy of the application log to attach to your email. Processing status and error information are logged, but extracted field values are never written to the log file.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CwSecondaryButton(
                                text = "Save Log File",
                                onClick = {
                                    saveLogFile(window) { message, error, file ->
                                        logSaveMessage = message
                                        logSaveError = error
                                        savedLogFile = file
                                    }
                                },
                            )
                            logSaveMessage?.let { msg ->
                                Text(
                                    text = msg,
                                    fontSize = 13.sp,
                                    color = BrandGreen,
                                )
                            }
                            savedLogFile?.let { file ->
                                Text(
                                    text = " ",
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = "Open folder",
                                    fontSize = 13.sp,
                                    color = BrandGreen,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable {
                                        try {
                                            if (Desktop.isDesktopSupported()) {
                                                Desktop.getDesktop().open(file.parentFile)
                                            }
                                        } catch (e: Exception) {
                                            println("[Help] Failed to open folder: ${e::class.simpleName}")
                                        }
                                    },
                                )
                            }
                            logSaveError?.let { msg ->
                                Text(
                                    text = msg,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE53E3E),
                                )
                            }
                        }
                    }
                }

                // Data Privacy section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Data Privacy")
                        HelpBullet(
                            text = "All data processing happens locally on this computer. No information is transmitted over the internet.",
                        )
                        HelpBullet(
                            text = "The application does not require an internet connection to operate.",
                        )
                        HelpBullet(
                            text = "Please follow your organization's policies for handling protected health information (PHI).",
                        )
                    }
                }

                // Data Validation section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Data Validation")
                        HelpBullet(
                            text = "Automated extraction may produce incomplete or inaccurate results depending on PDF formatting.",
                        )
                        HelpBullet(
                            text = "Always review all extracted data before relying on it for scheduling or clinical purposes.",
                        )
                        HelpBullet(
                            text = "Check the Warnings section on the results screen for fields that could not be confidently extracted.",
                        )
                    }
                }

                // What's New section
                ChangelogCard()

                // Licensing section
                LicensingCard()
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            CwSecondaryButton(
                text = "Back",
                onClick = onBack,
            )
        }
    }
}

/**
 * A numbered step in the Getting Started section.
 */
@Composable
private fun HelpStep(
    number: String,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$number.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = BrandGreen,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A bullet-point item for the Supported Formats and Tips sections.
 */
@Composable
private fun HelpBullet(
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "\u2022",
            fontSize = 14.sp,
            color = SoftGray,
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Open-source component entry for the licensing section.
 */
private data class OpenSourceComponent(
    val name: String,
    val license: String,
)

/** Third-party open-source components bundled with the application. */
private val OPEN_SOURCE_COMPONENTS = listOf(
    OpenSourceComponent("Apache PDFBox", "Apache License 2.0"),
    OpenSourceComponent("Tabula-java", "MIT License"),
    OpenSourceComponent("Apache POI", "Apache License 2.0"),
    OpenSourceComponent("Compose Multiplatform", "Apache License 2.0"),
    OpenSourceComponent("Kotlin", "Apache License 2.0"),
    OpenSourceComponent("kotlinx-serialization", "Apache License 2.0"),
    OpenSourceComponent("Navigation Compose", "Apache License 2.0"),
    OpenSourceComponent("Reorderable", "Apache License 2.0"),
    OpenSourceComponent("OpenJDK", "GPL v2 with Classpath Exception"),
)

/**
 * A single changelog entry representing one release version.
 *
 * @param version version string (e.g. "1.0.0") or "Unreleased"
 * @param date release date or targeting note (e.g. "2026-03-07" or "targeting v1.1.0")
 * @param description optional brief description for releases without categorized items
 * @param categories map of category name (Added/Changed/Fixed) to list of change descriptions
 */
private data class ChangelogEntry(
    val version: String,
    val date: String,
    val description: String? = null,
    val categories: Map<String, List<String>> = emptyMap(),
)

/** Application changelog entries, newest first. */
private val CHANGELOG_ENTRIES = listOf(
    ChangelogEntry(
        version = "1.1.0",
        date = "2026-03-10",
        categories = mapOf(
            "Added" to listOf(
                "Extract Provider/Doctor Name from referral PDFs",
                "Extract Special Instructions from referral PDFs",
                "Extract Examiner Name & Contact from referral PDFs",
                "Export format dropdown \u2014 choose XLSX, CSV, or TSV on the results screen",
                "In-app changelog (\u201cWhat\u2019s New\u201d on Help screen)",
            ),
        ),
    ),
    ChangelogEntry(
        version = "1.0.1",
        date = "2026-03-07",
        categories = mapOf(
            "Fixed" to listOf(
                "Crash on packaged installs due to missing java.sql module in jlink runtime image",
            ),
        ),
    ),
    ChangelogEntry(
        version = "1.0.0",
        date = "2026-03-07",
        description = "Initial release.",
    ),
)

/**
 * Collapsible "What's New" card displaying the application changelog,
 * grouped by version with categorized changes (Added/Changed/Fixed).
 */
@Composable
private fun ChangelogCard() {
    var changelogExpanded by remember { mutableStateOf(false) }

    CwCard {
        // Clickable header row — placed outside any padded Column so the
        // clickable ripple fills the full card width, matching LicensingCard.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { changelogExpanded = !changelogExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(text = "What\u2019s New")
            Text(
                text = if (changelogExpanded) "Collapse" else "Expand",
                fontSize = 13.sp,
                color = DeepInk,
            )
        }
        if (changelogExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                for ((index, entry) in CHANGELOG_ENTRIES.withIndex()) {
                    // Version header
                    val versionLabel = if (entry.version == "Unreleased") {
                        "Unreleased \u2014 ${entry.date}"
                    } else {
                        "v${entry.version} \u2014 ${entry.date}"
                    }
                    Text(
                        text = versionLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepInk,
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Simple description (e.g. "Initial release.")
                    entry.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Categorized changes
                    for ((category, items) in entry.categories) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = BrandGreen,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        for (item in items) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = "\u2022",
                                    fontSize = 14.sp,
                                    color = SoftGray,
                                    modifier = Modifier.width(16.dp),
                                )
                                Text(
                                    text = item,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SoftGray,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // Divider between versions (not after the last one)
                    if (index < CHANGELOG_ENTRIES.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = LightGray)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Licensing and attribution card displaying the application license,
 * copyright notice, and a collapsible list of open-source components.
 */
@Composable
private fun LicensingCard() {
    var componentsExpanded by remember { mutableStateOf(false) }

    CwCard {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(text = "Licensing")
            Text(
                text = "Copyright 2026 Carbon Works LLC.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Licensed under the Apache License, Version 2.0.",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftGray,
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = LightGray)
        }

        // Collapsible open-source components list — placed outside the padded
        // Column so the clickable ripple fills the full card width naturally
        // and is clipped by the card's own RoundedCornerShape(12.dp).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { componentsExpanded = !componentsExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open-Source Components",
                style = MaterialTheme.typography.titleSmall,
                color = DeepInk,
            )
            Text(
                text = if (componentsExpanded) "Collapse" else "Expand",
                fontSize = 13.sp,
                color = DeepInk,
            )
        }
        if (componentsExpanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text = "This software incorporates the following open-source components:",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray,
                )
                Spacer(modifier = Modifier.height(8.dp))
                for (component in OPEN_SOURCE_COMPONENTS) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "\u2022",
                            fontSize = 14.sp,
                            color = SoftGray,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = DeepInk)) {
                                    append(component.name)
                                }
                                append(" \u2014 ${component.license}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SoftGray,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Date format for the default log save filename. */
private val LOG_SAVE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Open a native save-as dialog and copy the application log files to the
 * user-chosen path. If multiple rotated log files exist, they are
 * concatenated in chronological order (oldest first).
 *
 * @param window optional AWT window to parent the FileDialog
 * @param onResult callback with (successMessage, errorMessage, savedFile)
 */
private fun saveLogFile(
    window: java.awt.Window?,
    onResult: (message: String?, error: String?, file: File?) -> Unit,
) {
    try {
        val logDir = LoggingSetup.logDirectory()
        if (logDir == null || !logDir.isDirectory) {
            println("[Help] Log directory not available")
            onResult(null, "Log directory not available", null)
            return
        }

        val dateStamp = LOG_SAVE_DATE_FORMAT.format(LocalDate.now())
        val defaultFilename = "batch-auth-processor-log-$dateStamp.txt"

        // Use Documents folder as default save location
        val documents = File(System.getProperty("user.home"), "Documents")
        val initialDir = if (documents.isDirectory) documents else File(System.getProperty("user.home"))

        val dialog = FileDialog(null as Frame?, "Save Log File", FileDialog.SAVE).apply {
            directory = initialDir.absolutePath
            file = defaultFilename
        }

        dialog.isVisible = true

        val chosenDir = dialog.directory
        val chosenFile = dialog.file

        if (chosenDir == null || chosenFile == null) {
            println("[Help] User cancelled log save dialog")
            return
        }

        val outputFile = File(chosenDir, chosenFile)

        // Collect log files in chronological order (oldest first).
        // Rotated files: app.2.log (oldest), app.1.log, app.log (newest).
        val logFiles = buildList {
            val appLog = File(logDir, "app.log")
            // Check for rotated files in reverse order (oldest first)
            for (i in 2 downTo 1) {
                val rotated = File(logDir, "app.$i.log")
                if (rotated.exists()) add(rotated)
            }
            if (appLog.exists()) add(appLog)
        }

        if (logFiles.isEmpty()) {
            println("[Help] No log files found in ${logDir.absolutePath}")
            onResult(null, "No log files found", null)
            return
        }

        // Concatenate all log files into the output file
        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { out ->
            for (logFile in logFiles) {
                logFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
        }

        println("[Help] Log saved to ${outputFile.absolutePath} (${logFiles.size} file(s) concatenated)")
        onResult("Log saved!", null, outputFile)
    } catch (e: Exception) {
        println("[Help] Failed to save log: ${e::class.simpleName}: ${e.message}")
        onResult(null, "Failed to save log: ${e.message ?: "Unknown error"}", null)
    }
}
