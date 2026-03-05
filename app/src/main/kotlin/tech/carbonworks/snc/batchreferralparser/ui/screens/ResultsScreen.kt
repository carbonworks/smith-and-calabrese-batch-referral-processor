package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.ServiceLine
import tech.carbonworks.snc.batchreferralparser.FeatureFlags
import tech.carbonworks.snc.batchreferralparser.output.ExportColumnConfig
import tech.carbonworks.snc.batchreferralparser.output.ExportPreferences
import tech.carbonworks.snc.batchreferralparser.output.SpreadsheetWriter
import tech.carbonworks.snc.batchreferralparser.util.PhiMask
import tech.carbonworks.snc.batchreferralparser.util.PhiPreferences
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwPrimaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.FilePathText
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandOrange
import tech.carbonworks.snc.batchreferralparser.ui.theme.CleanWhite
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.GreenTint
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/** Preferences key for the last-used save dialog directory. */
private const val PREF_LAST_SAVE_DIRECTORY = "lastSaveDirectory"

/** Timestamp format for the default save filename. */
private val SAVE_FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

/** User preferences node for persisting save directory. */
private val savePrefs: Preferences =
    Preferences.userRoot().node("tech/carbonworks/snc/batchreferralparser")

/**
 * Load the last-used save directory from preferences.
 *
 * @return the saved directory if it exists and is valid, or the user's
 *         Documents folder (or home directory) as a fallback
 */
private fun loadLastSaveDirectory(): File {
    val path = savePrefs.get(PREF_LAST_SAVE_DIRECTORY, null)
    if (path != null) {
        val dir = File(path)
        if (dir.isDirectory) return dir
    }
    // Default to Documents folder, falling back to user home
    val documents = File(System.getProperty("user.home"), "Documents")
    return if (documents.isDirectory) documents else File(System.getProperty("user.home"))
}

/**
 * Save the directory path to preferences for next session.
 */
private fun saveLastSaveDirectory(dir: File) {
    savePrefs.put(PREF_LAST_SAVE_DIRECTORY, dir.absolutePath)
}

/**
 * Results screen: displays extracted data as per-PDF referral cards, error summary,
 * warnings summary, and save-to-XLSX action.
 *
 * @param results the list of processing results (successes and failures)
 * @param onStartOver callback to return to the file selection screen
 * @param onNavigateToHelp callback to navigate to the Help screen
 * @param onNavigateToExportSettings callback to navigate to the Export Settings screen
 */
@Composable
fun ResultsScreen(
    results: List<ProcessedReferral>,
    onStartOver: () -> Unit,
    onNavigateToHelp: () -> Unit = {},
    onNavigateToExportSettings: () -> Unit = {},
) {
    println("[Results] ResultsScreen composed with ${results.size} result(s)")
    for ((i, r) in results.withIndex()) {
        val warnCount = r.warnings.size
        println("[Results]   [$i] fields=${r.fields != null} error=${r.error != null} warnings=$warnCount")
    }

    val successResults = results.filter { it.fields != null }
    val errorResults = results.filter { it.error != null }
    val warningResults = results.filter { it.warnings.isNotEmpty() }
    val totalWarnings = results.sumOf { it.warnings.size }
    val referralFields = successResults.mapNotNull { it.fields }
    println("[Results] successResults=${successResults.size}, errorResults=${errorResults.size}, warningResults=${warningResults.size}, totalWarnings=$totalWarnings, referralFields=${referralFields.size}")

    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var errorsExpanded by remember { mutableStateOf(false) }
    var warningsExpanded by remember { mutableStateOf(false) }

    // Hoisted masking state — drives recomposition of all masked fields.
    // Re-read the persisted preference first so that changes made in Settings
    // (or across app restarts) are reflected when this screen is composed.
    var isMasked by remember {
        PhiMask.refreshFromPreferences()
        mutableStateOf(PhiMask.maskingEnabled)
    }

    // Per-card mask overrides. When the global toggle changes, all overrides
    // are cleared so every card follows the new global state. Individual card
    // toggles insert entries here that take precedence over the global flag.
    val perCardMaskOverrides = remember { mutableStateMapOf<Int, Boolean>() }

    // Discovery cue: track whether animation should play
    val showDiscoveryCue = remember { mutableStateOf(!PhiPreferences.getToggleDismissed()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenTint)
            .padding(32.dp),
    ) {
        // Header row with title and eye toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Extraction Results",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DeepInk,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildSummaryText(successResults.size, errorResults.size, totalWarnings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                )
            }

            // Header icon buttons — eye toggle + help
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Eye toggle button with optional discovery cue
                PhiToggleButton(
                    isMasked = isMasked,
                    showDiscoveryCue = showDiscoveryCue.value,
                    onToggle = {
                        isMasked = !isMasked
                        PhiMask.maskingEnabled = isMasked
                        // Reset all per-card overrides to follow the new global state
                        perCardMaskOverrides.clear()
                        println("[Results] PHI masking toggled: ${if (isMasked) "masked" else "visible"}")
                        // Permanently dismiss the discovery cue on first toggle
                        if (showDiscoveryCue.value) {
                            PhiPreferences.setToggleDismissed(true)
                            showDiscoveryCue.value = false
                        }
                    },
                )
                IconButton(
                    onClick = onNavigateToHelp,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = "Help",
                        tint = BrandGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Summary cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(
                label = "Processed",
                value = "${results.size}",
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Successful",
                value = "${successResults.size}",
                valueColor = BrandGreen,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Warnings",
                value = "$totalWarnings",
                valueColor = if (totalWarnings > 0) BrandOrange else SoftGray,
                modifier = Modifier.weight(1f),
            )
            SummaryCard(
                label = "Failed",
                value = "${errorResults.size}",
                valueColor = if (errorResults.isNotEmpty()) Color(0xFFE53E3E) else SoftGray,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error summary (expandable)
        if (errorResults.isNotEmpty()) {
            CwCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { errorsExpanded = !errorsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            text = "Errors (${errorResults.size})",
                        )
                        Text(
                            text = if (errorsExpanded) "Collapse" else "Expand",
                            fontSize = 13.sp,
                            color = DeepInk,
                        )
                    }
                    if (errorsExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        for (result in errorResults) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                FilePathText(
                                    path = result.file.name,
                                    modifier = Modifier.width(200.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isMasked) PhiMask.maskDisplay(result.error ?: "Unknown error") else (result.error ?: "Unknown error"),
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Warnings summary (expandable)
        if (warningResults.isNotEmpty()) {
            CwCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { warningsExpanded = !warningsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            text = "Warnings ($totalWarnings)",
                        )
                        Text(
                            text = if (warningsExpanded) "Collapse" else "Expand",
                            fontSize = 13.sp,
                            color = DeepInk,
                        )
                    }
                    if (warningsExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        for (result in warningResults) {
                            // File name header for this group
                            Text(
                                text = result.file.name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepInk,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                            for (warning in result.warnings) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "[${warning.stage}]",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = BrandOrange,
                                        modifier = Modifier.width(100.dp),
                                    )
                                    Text(
                                        text = "${warning.field}: ${if (isMasked) PhiMask.maskDisplay(warning.message) else warning.message}",
                                        fontSize = 12.sp,
                                        color = SoftGray,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Data preview — per-PDF referral cards
        SectionHeader(text = "Data Preview")

        if (successResults.isEmpty()) {
            CwCard(
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No data to preview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftGray,
                    )
                }
            }
        } else {
            val lazyListState = rememberLazyListState()
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(successResults) { index, result ->
                        val cardMasked = perCardMaskOverrides[index] ?: isMasked
                        ReferralCard(
                            processedReferral = result,
                            isMasked = cardMasked,
                            onToggleMask = {
                                perCardMaskOverrides[index] = !cardMasked
                            },
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(lazyListState),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Save feedback — animated row above the action buttons
        AnimatedVisibility(
            visible = savedFile != null || saveError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                savedFile?.let { file ->
                    Text(
                        text = file.name,
                        fontSize = 13.sp,
                        color = BrandOrange,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(file)
                                }
                            } catch (e: Exception) {
                                println("[Results] Failed to open file: ${e::class.simpleName}")
                            }
                        },
                    )
                    Text(
                        text = " ",
                        fontSize = 13.sp,
                        color = BrandOrange,
                    )
                    Text(
                        text = "Open folder",
                        fontSize = 13.sp,
                        color = BrandOrange,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            try {
                                if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(file.parentFile)
                                }
                            } catch (e: Exception) {
                                println("[Results] Failed to open folder: ${e::class.simpleName}")
                            }
                        },
                    )
                }
                saveError?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = Color(0xFFE53E3E),
                    )
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CwSecondaryButton(
                text = "Start Over",
                onClick = onStartOver,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (FeatureFlags.EXPORT_COLUMN_CONFIG) {
                IconButton(
                    onClick = onNavigateToExportSettings,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Export Settings",
                        tint = BrandGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            CwPrimaryButton(
                text = "Export",
                onClick = {
                    saveToXlsx(referralFields) { message, error, file ->
                        saveMessage = message
                        saveError = error
                        savedFile = file
                    }
                },
                enabled = referralFields.isNotEmpty(),
            )
        }
    }
}

/**
 * Eye toggle button for masking/unmasking PHI data.
 *
 * When [showDiscoveryCue] is true, the button pulses with a subtle scale
 * animation on a repeating 15-second cycle to draw the user's attention.
 */
@Composable
private fun PhiToggleButton(
    isMasked: Boolean,
    showDiscoveryCue: Boolean,
    onToggle: () -> Unit,
) {
    // Discovery cue: double scale pulse on a repeating 8-second cycle.
    // Idles for 3 seconds, then delivers two quick pulses (each 200ms
    // grow + 200ms shrink), then idles again until the cycle repeats.
    val pulseScale = if (showDiscoveryCue) {
        val infiniteTransition = rememberInfiniteTransition(label = "phi-toggle-pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 8_000
                    1f at 0
                    1f at 3_000        // 3s initial wait
                    1.25f at 3_200     // pulse 1 grow (200ms)
                    1f at 3_400        // pulse 1 shrink (200ms)
                    1.25f at 3_600     // pulse 2 grow (200ms)
                    1f at 3_800        // pulse 2 shrink (200ms)
                    1f at 8_000        // idle to end
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "phi-toggle-scale",
        )
        scale
    } else {
        1f
    }

    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
    ) {
        Icon(
            imageVector = if (isMasked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (isMasked) "Show extracted data" else "Mask extracted data",
            tint = BrandGreen,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * A single referral card showing extracted data from one PDF.
 *
 * Layout:
 * - Header: source filename + "Open PDF" link
 * - Body: left column (patient metadata) + right column (services)
 * - Footer: invoice/case footer fields
 */
@Composable
private fun ReferralCard(
    processedReferral: ProcessedReferral,
    isMasked: Boolean,
    onToggleMask: () -> Unit = {},
) {
    val fields = processedReferral.fields ?: return
    val file = processedReferral.file

    CwCard {
        SelectionContainer {
            Column(modifier = Modifier.padding(16.dp)) {
                // Card header — filename + per-card mask toggle + Open PDF link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = file.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepInk,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onToggleMask,
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = if (isMasked) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (isMasked) "Unmask this referral" else "Mask this referral",
                            tint = BrandGreen,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OpenPdfLink(file = file)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = LightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Per-card warnings (completeness check)
                if (processedReferral.warnings.isNotEmpty()) {
                    val missingFieldWarnings = processedReferral.warnings.filter { it.stage == "completeness" }
                    if (missingFieldWarnings.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    BrandOrange.copy(alpha = 0.08f),
                                    shape = MaterialTheme.shapes.small,
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "Missing fields:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandOrange,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(
                                text = missingFieldWarnings.joinToString(", ") { it.field },
                                fontSize = 12.sp,
                                color = BrandOrange.copy(alpha = 0.85f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Body — patient metadata (left) + services (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Left side — patient metadata (~60%)
                    Column(modifier = Modifier.weight(0.6f)) {
                        PatientMetadataSection(fields = fields, isMasked = isMasked)
                    }

                    // Right side — services (~40%)
                    Column(modifier = Modifier.weight(0.4f)) {
                        ServicesSection(services = fields.services, isMasked = isMasked)
                    }
                }

                // Footer — invoice/case fields (only if any are present)
                val hasFooterFields = listOf(
                    fields.federalTaxId,
                    fields.vendorNumber,
                    fields.caseNumberFullFooter,
                    fields.assignedCode,
                    fields.dccNumber,
                ).any { !it.isNullOrEmpty() }

                if (hasFooterFields) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = LightGray, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FooterSection(fields = fields, isMasked = isMasked)
                }
            }
        }
    }
}

/**
 * Clickable "Open PDF" text that opens the file in the OS default PDF reader.
 * Handles missing Desktop support gracefully.
 */
@Composable
private fun OpenPdfLink(file: File) {
    val desktopSupported = remember {
        try {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
        } catch (_: Exception) {
            false
        }
    }

    if (desktopSupported) {
        Text(
            text = "Open PDF",
            fontSize = 13.sp,
            color = BrandGreen,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                try {
                    Desktop.getDesktop().open(file)
                } catch (e: Exception) {
                    println("[Results] Failed to open PDF: ${e::class.simpleName}")
                }
            },
        )
    }
}

/**
 * Patient metadata displayed as label/value pairs, skipping empty fields.
 */
@Composable
private fun PatientMetadataSection(fields: ReferralFields, isMasked: Boolean) {
    // Build full name from parts
    val fullName = listOfNotNull(
        fields.firstName,
        fields.middleName,
        fields.lastName,
    ).filter { it.isNotEmpty() }.joinToString(" ")

    // Build address line
    val addressParts = listOfNotNull(
        fields.streetAddress,
    ).filter { it.isNotEmpty() }
    val cityStateZip = listOfNotNull(
        fields.city,
        fields.state,
    ).filter { it.isNotEmpty() }.joinToString(", ").let { csz ->
        if (csz.isNotEmpty() && !fields.zipCode.isNullOrEmpty()) {
            "$csz ${fields.zipCode}"
        } else if (!fields.zipCode.isNullOrEmpty()) {
            fields.zipCode
        } else {
            csz
        }
    }

    val metadataFields = buildList {
        if (fullName.isNotEmpty()) add("Claimant" to fullName)
        if (!fields.dob.isNullOrEmpty()) add("DOB" to fields.dob)
        if (!fields.caseId.isNullOrEmpty()) add("Case ID" to fields.caseId)
        if (!fields.authorizationNumber.isNullOrEmpty()) add("Authorization #" to fields.authorizationNumber)
        if (!fields.requestId.isNullOrEmpty()) add("Request ID" to fields.requestId)
        if (!fields.dateOfIssue.isNullOrEmpty()) add("Date of Issue" to fields.dateOfIssue)
        if (!fields.applicantName.isNullOrEmpty()) add("Applicant" to fields.applicantName)
        if (!fields.appointmentDate.isNullOrEmpty()) add("Appointment" to buildString {
            append(fields.appointmentDate)
            if (!fields.appointmentTime.isNullOrEmpty()) {
                append(" at ")
                append(fields.appointmentTime)
            }
        })
        if (addressParts.isNotEmpty()) {
            add("Address" to addressParts.joinToString(", "))
            if (cityStateZip.isNotEmpty()) add("" to cityStateZip)
        } else if (cityStateZip.isNotEmpty()) {
            // Street address missing — show city/state/zip with "Address" label
            // so it doesn't appear as a label-less continuation row.
            add("Address" to cityStateZip)
        }
        if (!fields.phone.isNullOrEmpty()) add("Phone" to fields.phone)
    }

    for ((label, value) in metadataFields) {
        MetadataRow(label = label, value = value, isMasked = isMasked)
    }
}

/**
 * A single label/value row in the metadata section. The value is copyable
 * via click-to-copy, respecting PHI masking state.
 */
@Composable
private fun MetadataRow(label: String, value: String, isMasked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = SoftGray,
                modifier = Modifier.width(100.dp),
            )
        } else {
            // Continuation line (e.g., city/state/zip under address)
            Spacer(modifier = Modifier.width(100.dp))
        }
        Text(
            text = if (isMasked) PhiMask.maskDisplay(value) else value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Services authorized section showing CPT code, description, and fee per service.
 */
@Composable
private fun ServicesSection(services: List<ServiceLine>, isMasked: Boolean) {
    Text(
        text = "Services Authorized",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = DeepInk,
        modifier = Modifier.padding(bottom = 6.dp),
    )

    if (services.isEmpty()) {
        Text(
            text = "No services found",
            fontSize = 12.sp,
            color = SoftGray,
        )
    } else {
        for ((index, service) in services.withIndex()) {
            if (index > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = LightGray.copy(alpha = 0.6f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(4.dp))
            }
            ServiceItem(service = service, isMasked = isMasked)
        }
    }
}

/**
 * A single service line item display with copyable values.
 */
@Composable
private fun ServiceItem(service: ServiceLine, isMasked: Boolean) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isMasked) PhiMask.maskDisplay(service.cptCode) else service.cptCode,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
            if (!service.fee.isNullOrEmpty()) {
                Text(
                    text = if (isMasked) PhiMask.maskDisplay(service.fee) else service.fee,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        if (!service.description.isNullOrEmpty()) {
            Text(
                text = if (isMasked) PhiMask.maskDisplay(service.description) else service.description,
                fontSize = 11.sp,
                color = SoftGray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Footer section showing invoice/case fields in a subtle secondary style.
 * Each value is copyable via click-to-copy, respecting PHI masking state.
 */
@Composable
private fun FooterSection(fields: ReferralFields, isMasked: Boolean) {
    val footerFields = buildList {
        if (!fields.federalTaxId.isNullOrEmpty()) add("Federal Tax ID" to fields.federalTaxId)
        if (!fields.vendorNumber.isNullOrEmpty()) add("Vendor Number" to fields.vendorNumber)
        if (!fields.caseNumberFullFooter.isNullOrEmpty()) add("Case Number" to fields.caseNumberFullFooter)
        if (!fields.assignedCode.isNullOrEmpty()) add("Assigned Code" to fields.assignedCode)
        if (!fields.dccNumber.isNullOrEmpty()) add("DCC Number" to fields.dccNumber)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        for ((label, value) in footerFields) {
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = SoftGray,
                )
                Text(
                    text = if (isMasked) PhiMask.maskDisplay(value) else value,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DeepInk,
) {
    CwCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = SoftGray,
            )
        }
    }
}

/**
 * Open a native save dialog and write extracted referral data to the
 * user-chosen XLSX path.
 *
 * Uses [java.awt.FileDialog] in save mode for a native OS dialog.
 * The last-used save directory is remembered across sessions via
 * [java.util.prefs.Preferences]. On first launch, defaults to the
 * user's Documents folder.
 */
private fun saveToXlsx(
    referralFields: List<ReferralFields>,
    onResult: (message: String?, error: String?, file: File?) -> Unit,
) {
    try {
        val defaultFilename = "patient-referrals-${SAVE_FILENAME_TIMESTAMP.format(LocalDateTime.now())}.xlsx"
        val initialDir = loadLastSaveDirectory()

        val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE).apply {
            directory = initialDir.absolutePath
            file = defaultFilename
            // AWT FileDialog on Windows/macOS supports setFilenameFilter but it is
            // unreliable; setting the default filename with .xlsx extension is the
            // most portable way to guide the user toward the correct file type.
        }

        dialog.isVisible = true

        val chosenDir = dialog.directory
        val chosenFile = dialog.file

        // User cancelled the dialog
        if (chosenDir == null || chosenFile == null) {
            println("[Save] User cancelled save dialog")
            return
        }

        // Ensure .xlsx extension
        val finalName = if (chosenFile.endsWith(".xlsx", ignoreCase = true)) {
            chosenFile
        } else {
            "$chosenFile.xlsx"
        }
        val outputFile = File(chosenDir, finalName)

        println("[Save] Writing ${referralFields.size} referral(s) to file")

        // Remember this directory for next time
        saveLastSaveDirectory(File(chosenDir))

        // Write to a temp directory, then copy to the user-chosen path.
        // SpreadsheetWriter.write generates its own filename, so we write
        // to a temp dir and then move the result to the chosen location.
        val tempDir = kotlin.io.path.createTempDirectory("snc-export").toFile()
        try {
            val columnConfig = if (FeatureFlags.EXPORT_COLUMN_CONFIG) {
                ExportPreferences.load()
            } else {
                ExportColumnConfig.default()
            }
            val tempFile = SpreadsheetWriter.write(referralFields, tempDir, columnConfig = columnConfig)

            // Move (or copy) to user-chosen path
            outputFile.parentFile?.mkdirs()
            tempFile.copyTo(outputFile, overwrite = true)
            tempFile.delete()
            tempDir.delete()
        } catch (e: Exception) {
            // Clean up temp dir on failure
            tempDir.deleteRecursively()
            throw e
        }

        println("[Save] Saved successfully")
        onResult("Saved to: ${outputFile.absolutePath}", null, outputFile)
    } catch (e: Exception) {
        println("[Save] FAILED: ${e::class.simpleName}")
        onResult(null, "Failed to save: ${e.message ?: "Unknown error"}", null)
    }
}

private fun buildSummaryText(successCount: Int, errorCount: Int, warningCount: Int): String {
    val parts = mutableListOf<String>()
    if (successCount > 0) {
        parts.add("$successCount successful extraction${if (successCount != 1) "s" else ""}")
    }
    if (warningCount > 0) {
        parts.add("$warningCount warning${if (warningCount != 1) "s" else ""}")
    }
    if (errorCount > 0) {
        parts.add("$errorCount failed")
    }
    return if (parts.isEmpty()) "No files processed" else parts.joinToString(", ")
}
