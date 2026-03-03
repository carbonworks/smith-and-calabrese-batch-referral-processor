package tech.carbonworks.snc.batchreferralparser.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.FeatureFlags
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import tech.carbonworks.snc.batchreferralparser.util.PhiPreferences

/**
 * Settings screen: allows the user to configure application preferences.
 *
 * Contains a "Privacy" section with a data-masking toggle and, when
 * [FeatureFlags.EXPORT_COLUMN_CONFIG] is enabled, a navigation row to
 * the dedicated Export Columns configuration screen.
 *
 * @param onBack callback to return to the file selection screen
 * @param onNavigateToExportSettings callback to navigate to the Export Columns screen
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToExportSettings: () -> Unit = {},
) {
    var showByDefault by remember { mutableStateOf(PhiPreferences.getShowByDefault()) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite)
            .padding(32.dp),
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = DeepInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configure application preferences",
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
                // Privacy section
                CwCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(text = "Privacy")

                        Spacer(modifier = Modifier.height(8.dp))

                        // Toggle row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show extracted data by default",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DeepInk,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "When enabled, extracted field values are displayed unmasked on the results screen. " +
                                        "When disabled, data is masked by default for privacy protection and can be " +
                                        "revealed using the eye toggle on the results screen.",
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    lineHeight = 18.sp,
                                )
                            }

                            Switch(
                                checked = showByDefault,
                                onCheckedChange = { checked ->
                                    showByDefault = checked
                                    PhiPreferences.setShowByDefault(checked)
                                    // Dismiss the discovery cue permanently when the setting is changed
                                    PhiPreferences.setToggleDismissed(true)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BrandGreen,
                                    checkedTrackColor = BrandGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = LightGray,
                                    uncheckedTrackColor = LightGray.copy(alpha = 0.3f),
                                ),
                            )
                        }
                    }
                }

                // Export Columns navigation row (feature-flagged)
                if (FeatureFlags.EXPORT_COLUMN_CONFIG) {
                    CwCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToExportSettings() }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Export Columns",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DeepInk,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Configure which columns appear in the exported spreadsheet and their order.",
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                    lineHeight = 18.sp,
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Open export column settings",
                                tint = SoftGray,
                                modifier = Modifier.size(24.dp),
                            )
                        }
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
