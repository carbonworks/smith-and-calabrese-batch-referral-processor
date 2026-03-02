package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.ServiceLine
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
import java.io.File

/**
 * Results screen: displays extracted data as per-PDF referral cards, error summary,
 * warnings summary, and save-to-XLSX action.
 *
 * @param results the list of processing results (successes and failures)
 * @param onStartOver callback to return to the file selection screen
 */
@Composable
fun ResultsScreen(
    results: List<ProcessedReferral>,
    onStartOver: () -> Unit,
) {
    println("[Results] ResultsScreen composed with ${results.size} result(s)")
    for ((i, r) in results.withIndex()) {
        val warnCount = r.warnings.size
        println("[Results]   [$i] file=${r.file.name} fields=${r.fields != null} error=${r.error} warnings=$warnCount")
    }

    val successResults = results.filter { it.fields != null }
    val errorResults = results.filter { it.error != null }
    val warningResults = results.filter { it.warnings.isNotEmpty() }
    val totalWarnings = results.sumOf { it.warnings.size }
    val referralFields = successResults.mapNotNull { it.fields }
    println("[Results] successResults=${successResults.size}, errorResults=${errorResults.size}, warningResults=${warningResults.size}, totalWarnings=$totalWarnings, referralFields=${referralFields.size}")

    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var errorsExpanded by remember { mutableStateOf(false) }
    var warningsExpanded by remember { mutableStateOf(false) }

    // Hoisted masking state — drives recomposition of all masked fields
    var isMasked by remember { mutableStateOf(PhiMask.maskingEnabled) }

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

            // Eye toggle button with optional discovery cue
            PhiToggleButton(
                isMasked = isMasked,
                showDiscoveryCue = showDiscoveryCue.value,
                onToggle = {
                    isMasked = !isMasked
                    PhiMask.maskingEnabled = isMasked
                    // Permanently dismiss the discovery cue on first toggle
                    if (showDiscoveryCue.value) {
                        PhiPreferences.setToggleDismissed(true)
                        showDiscoveryCue.value = false
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

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
                                    text = PhiMask.maskDisplay(result.error ?: "Unknown error"),
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
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        text = "${warning.field}: ${PhiMask.maskDisplay(warning.message)}",
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
                    items(successResults) { result ->
                        ReferralCard(
                            processedReferral = result,
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(lazyListState),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save status messages
        saveMessage?.let { msg ->
            Text(
                text = msg,
                fontSize = 13.sp,
                color = BrandGreen,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        saveError?.let { msg ->
            Text(
                text = msg,
                fontSize = 13.sp,
                color = Color(0xFFE53E3E),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CwSecondaryButton(
                text = "Start Over",
                onClick = onStartOver,
            )
            Spacer(modifier = Modifier.width(12.dp))
            CwPrimaryButton(
                text = "Save to XLSX",
                onClick = {
                    saveToXlsx(results, referralFields) { message, error ->
                        saveMessage = message
                        saveError = error
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
    // Discovery cue: subtle scale pulse on a repeating 15-second cycle.
    // The pulse grows to 1.25x over 400ms, shrinks back over 400ms, then
    // idles at 1x for the remaining ~14.2 seconds before repeating.
    val pulseScale = if (showDiscoveryCue) {
        val infiniteTransition = rememberInfiniteTransition(label = "phi-toggle-pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 15_000
                    1f at 0
                    1.25f at 400
                    1f at 800
                    1f at 15_000
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
            tint = SoftGray,
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
) {
    val fields = processedReferral.fields ?: return
    val file = processedReferral.file

    CwCard {
        Column(modifier = Modifier.padding(16.dp)) {
            // Card header — filename + Open PDF link
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
                Spacer(modifier = Modifier.width(12.dp))
                OpenPdfLink(file = file)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Body — patient metadata (left) + services (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left side — patient metadata (~60%)
                Column(modifier = Modifier.weight(0.6f)) {
                    PatientMetadataSection(fields = fields)
                }

                // Right side — services (~40%)
                Column(modifier = Modifier.weight(0.4f)) {
                    ServicesSection(services = fields.services)
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
                FooterSection(fields = fields)
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
                    println("[Results] Failed to open PDF: ${e.message}")
                }
            },
        )
    }
}

/**
 * Patient metadata displayed as label/value pairs, skipping empty fields.
 */
@Composable
private fun PatientMetadataSection(fields: ReferralFields) {
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
        if (addressParts.isNotEmpty()) add("Address" to addressParts.joinToString(", "))
        if (cityStateZip.isNotEmpty()) add("" to cityStateZip)
        if (!fields.phone.isNullOrEmpty()) add("Phone" to fields.phone)
    }

    for ((label, value) in metadataFields) {
        MetadataRow(label = label, value = value)
    }
}

/**
 * A single label/value row in the metadata section.
 */
@Composable
private fun MetadataRow(label: String, value: String) {
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
            text = PhiMask.maskDisplay(value),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = DeepInk,
        )
    }
}

/**
 * Services authorized section showing CPT code, description, and fee per service.
 */
@Composable
private fun ServicesSection(services: List<ServiceLine>) {
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
            ServiceItem(service = service)
        }
    }
}

/**
 * A single service line item display.
 */
@Composable
private fun ServiceItem(service: ServiceLine) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = PhiMask.maskDisplay(service.cptCode),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = DeepInk,
            )
            if (!service.fee.isNullOrEmpty()) {
                Text(
                    text = PhiMask.maskDisplay(service.fee),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DeepInk,
                )
            }
        }
        if (!service.description.isNullOrEmpty()) {
            Text(
                text = PhiMask.maskDisplay(service.description),
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
 */
@Composable
private fun FooterSection(fields: ReferralFields) {
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
                    text = PhiMask.maskDisplay(value),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DeepInk,
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
 * Save extracted referral data to an XLSX file and open the output directory.
 */
private fun saveToXlsx(
    results: List<ProcessedReferral>,
    referralFields: List<ReferralFields>,
    onResult: (message: String?, error: String?) -> Unit,
) {
    try {
        // Default output directory: parent of first source PDF
        val outputDir = results.firstOrNull()?.file?.parentFile ?: File(System.getProperty("user.home"))
        println("[Save] Writing ${referralFields.size} referral(s) to XLSX in: ${outputDir.absolutePath}")

        val outputFile = SpreadsheetWriter.write(referralFields, outputDir)
        println("[Save] Saved: ${outputFile.absolutePath}")

        onResult("Saved to: ${outputFile.absolutePath}", null)

        // Open the output directory in the OS file manager
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(outputDir)
            }
        } catch (_: Exception) {
            // Non-critical: file was saved successfully, just couldn't open the folder
        }
    } catch (e: Exception) {
        println("[Save] FAILED: ${e.message}")
        onResult(null, "Failed to save: ${e.message ?: "Unknown error"}")
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
