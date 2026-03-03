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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import java.awt.Desktop
import java.net.URI

/**
 * Help & Support screen: displays usage instructions, supported formats,
 * tips, and support contact information.
 *
 * @param onBack callback to return to the main (file selection) screen
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()

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
            text = "Learn how to use the batch referral processor",
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
                            text = "Select PDF referral files using the file picker or drag-and-drop.",
                        )
                        HelpStep(
                            number = "2",
                            text = "Click \"Process\" to extract data from all selected files.",
                        )
                        HelpStep(
                            number = "3",
                            text = "Review the extracted data in the preview table.",
                        )
                        HelpStep(
                            number = "4",
                            text = "Click \"Save to XLSX\" to export the results as a spreadsheet.",
                        )
                    }
                }

                // Supported Formats section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Supported Formats")
                        HelpBullet(
                            text = "The tool processes SSA/DDS consultative examination referral PDFs.",
                        )
                        HelpBullet(
                            text = "Up to 50 PDFs can be processed in a single batch.",
                        )
                        HelpBullet(
                            text = "Output is saved as an XLSX spreadsheet compatible with Excel and Google Sheets.",
                        )
                    }
                }

                // Tips section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Tips")
                        HelpBullet(
                            text = "If fields are missing, check the warnings panel on the results screen.",
                        )
                        HelpBullet(
                            text = "The file picker remembers the last directory used.",
                        )
                        HelpBullet(
                            text = "Drag and drop multiple files at once for faster batch loading.",
                        )
                        HelpBullet(
                            text = "Extracted data is masked by default for privacy. Use the eye toggle on the results screen to reveal values, or change the default in Settings.",
                        )
                        HelpBullet(
                            text = "Triple-click on any field value in the results to select the entire value for copying.",
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
                    }
                }
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
        )
    }
}
