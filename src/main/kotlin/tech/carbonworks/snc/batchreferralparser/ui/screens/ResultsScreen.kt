package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.extraction.Confidence
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.output.SpreadsheetWriter
import tech.carbonworks.snc.batchreferralparser.ui.components.CwAccentButton
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
 * Results screen: displays extracted data preview, error summary, and save-to-XLSX action.
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
        println("[Results]   [$i] file=${r.file.name} fields=${r.fields != null} error=${r.error}")
    }

    val successResults = results.filter { it.fields != null }
    val errorResults = results.filter { it.error != null }
    val referralFields = successResults.mapNotNull { it.fields }
    println("[Results] successResults=${successResults.size}, errorResults=${errorResults.size}, referralFields=${referralFields.size}")

    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var errorsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GreenTint)
            .padding(32.dp),
    ) {
        // Header
        Text(
            text = "Extraction Results",
            style = MaterialTheme.typography.headlineSmall,
            color = DeepInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = buildSummaryText(successResults.size, errorResults.size),
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
        )

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
                                    text = result.error ?: "Unknown error",
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Data preview table
        SectionHeader(text = "Data Preview")

        CwCard(
            modifier = Modifier.weight(1f),
        ) {
            if (referralFields.isEmpty()) {
                println("[Results] Table: referralFields is EMPTY — showing 'No data to preview'")
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
            } else {
                val horizontalScroll = rememberScrollState()
                val columnHeadings = SpreadsheetWriter.COLUMN_HEADINGS
                val rowData = referralFields.map { extractRowValues(it) }
                println("[Results] Table: ${rowData.size} row(s), ${columnHeadings.size} column(s)")
                for ((i, row) in rowData.withIndex()) {
                    val nonEmpty = row.count { it.isNotEmpty() }
                    val populatedCols = row.withIndex()
                        .filter { it.value.isNotEmpty() }
                        .joinToString(", ") { "[${it.index}] ${columnHeadings[it.index]}" }
                    println("[Results]   Row $i: $nonEmpty/${row.size} non-empty — columns: $populatedCols")
                }

                val verticalScroll = rememberScrollState()

                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp, bottom = 12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScroll)
                                .verticalScroll(verticalScroll),
                        ) {
                            // Header row
                            Row(
                                modifier = Modifier
                                    .background(LightGray.copy(alpha = 0.4f))
                                    .padding(horizontal = 4.dp),
                            ) {
                                // Row number column
                                TableHeaderCell(text = "#", width = 40)
                                for (heading in columnHeadings) {
                                    TableHeaderCell(text = heading, width = 140)
                                }
                            }

                            // Data rows
                            for ((rowIndex, row) in rowData.withIndex()) {
                                val fields = referralFields[rowIndex]
                                val hasLow = fields.hasLowConfidenceFields()
                                val rowBackground = if (hasLow) {
                                    BrandOrange.copy(alpha = 0.08f)
                                } else {
                                    CleanWhite
                                }

                                Row(
                                    modifier = Modifier
                                        .background(rowBackground)
                                        .padding(horizontal = 4.dp),
                                ) {
                                    TableDataCell(
                                        text = "${rowIndex + 1}",
                                        width = 40,
                                    )
                                    for ((colIndex, value) in row.withIndex()) {
                                        val confidence = getFieldConfidence(fields, colIndex)
                                        val cellBackground = when (confidence) {
                                            Confidence.LOW -> BrandOrange.copy(alpha = 0.15f)
                                            else -> Color.Transparent
                                        }
                                        TableDataCell(
                                            text = value,
                                            width = 140,
                                            backgroundColor = cellBackground,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Visible, draggable scrollbars
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(verticalScroll),
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScroll),
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(end = 12.dp),
                    )
                }
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

@Composable
private fun TableHeaderCell(text: String, width: Int) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DeepInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TableDataCell(
    text: String,
    width: Int,
    backgroundColor: Color = Color.Transparent,
) {
    Box(
        modifier = Modifier
            .width(width.dp)
            .background(backgroundColor)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = DeepInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Extract display values from a ReferralFields in the same order as SpreadsheetWriter.COLUMN_HEADINGS.
 */
private fun extractRowValues(referral: ReferralFields): List<String> {
    val services = referral.services.joinToString(", ") { it.cptCode }
    val lowConfidenceFlag = if (referral.hasLowConfidenceFields()) "YES" else ""

    return listOf(
        referral.firstName.value.orEmpty(),
        referral.middleName.value.orEmpty(),
        referral.lastName.value.orEmpty(),
        referral.caseId.value.orEmpty(),
        referral.authorizationNumber.value.orEmpty(),
        referral.requestId.value.orEmpty(),
        referral.dateOfIssue.value.orEmpty(),
        referral.dob.value.orEmpty(),
        referral.applicantName.value.orEmpty(),
        referral.appointmentDate.value.orEmpty(),
        referral.appointmentTime.value.orEmpty(),
        referral.streetAddress.value.orEmpty(),
        referral.city.value.orEmpty(),
        referral.state.value.orEmpty(),
        referral.zipCode.value.orEmpty(),
        referral.phone.value.orEmpty(),
        services,
        referral.federalTaxId.value.orEmpty(),
        referral.vendorNumber.value.orEmpty(),
        referral.caseNumberFullFooter.value.orEmpty(),
        referral.assignedCode.value.orEmpty(),
        referral.dccNumber.value.orEmpty(),
        lowConfidenceFlag,
    )
}

/**
 * Get the confidence level for a field by its column index.
 * Matches the order of SpreadsheetWriter.COLUMN_HEADINGS.
 */
private fun getFieldConfidence(referral: ReferralFields, colIndex: Int): Confidence? {
    return when (colIndex) {
        0 -> referral.firstName.confidence
        1 -> referral.middleName.confidence
        2 -> referral.lastName.confidence
        3 -> referral.caseId.confidence
        4 -> referral.authorizationNumber.confidence
        5 -> referral.requestId.confidence
        6 -> referral.dateOfIssue.confidence
        7 -> referral.dob.confidence
        8 -> referral.applicantName.confidence
        9 -> referral.appointmentDate.confidence
        10 -> referral.appointmentTime.confidence
        11 -> referral.streetAddress.confidence
        12 -> referral.city.confidence
        13 -> referral.state.confidence
        14 -> referral.zipCode.confidence
        15 -> referral.phone.confidence
        16 -> referral.servicesConfidence
        17 -> referral.federalTaxId.confidence
        18 -> referral.vendorNumber.confidence
        19 -> referral.caseNumberFullFooter.confidence
        20 -> referral.assignedCode.confidence
        21 -> referral.dccNumber.confidence
        else -> null
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

private fun buildSummaryText(successCount: Int, errorCount: Int): String {
    val parts = mutableListOf<String>()
    if (successCount > 0) {
        parts.add("$successCount successful extraction${if (successCount != 1) "s" else ""}")
    }
    if (errorCount > 0) {
        parts.add("$errorCount failed")
    }
    return if (parts.isEmpty()) "No files processed" else parts.joinToString(", ")
}
