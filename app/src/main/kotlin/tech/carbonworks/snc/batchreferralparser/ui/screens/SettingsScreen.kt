package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.FeatureFlags
import tech.carbonworks.snc.batchreferralparser.output.DEFAULT_FIELD_ORDER
import tech.carbonworks.snc.batchreferralparser.output.ExportColumn
import tech.carbonworks.snc.batchreferralparser.output.ExportColumnConfig
import tech.carbonworks.snc.batchreferralparser.output.ExportPreferences
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import tech.carbonworks.snc.batchreferralparser.util.PhiPreferences

/** Field IDs included in the "Essential Only" preset. */
private val ESSENTIAL_FIELD_IDS = setOf(
    "firstName",
    "lastName",
    "dob",
    "caseId",
    "authorizationNumber",
    "appointmentDate",
    "appointmentTime",
    "services",
)

/**
 * Settings screen: allows the user to configure application preferences.
 *
 * Contains a "Privacy" section with a data-masking toggle and, when
 * [FeatureFlags.EXPORT_COLUMN_CONFIG] is enabled, an "Export Columns"
 * section for customising the XLSX output layout.
 *
 * @param onBack callback to return to the file selection screen
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    var showByDefault by remember { mutableStateOf(PhiPreferences.getShowByDefault()) }
    var columnConfig by remember { mutableStateOf(ExportPreferences.load()) }

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

                // Export Columns section (feature-flagged)
                if (FeatureFlags.EXPORT_COLUMN_CONFIG) {
                    CwCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(text = "Export Columns")

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Choose which columns appear in the exported spreadsheet and their order.",
                                fontSize = 12.sp,
                                color = SoftGray,
                                lineHeight = 18.sp,
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Preset buttons row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CwSecondaryButton(
                                    text = "All Fields",
                                    onClick = {
                                        columnConfig = ExportColumnConfig.default()
                                        ExportPreferences.save(columnConfig)
                                    },
                                )
                                CwSecondaryButton(
                                    text = "Essential Only",
                                    onClick = {
                                        columnConfig = ExportColumnConfig(
                                            columns = DEFAULT_FIELD_ORDER.map { (fieldId, displayName) ->
                                                ExportColumn.Field(
                                                    fieldId = fieldId,
                                                    displayName = displayName,
                                                    enabled = fieldId in ESSENTIAL_FIELD_IDS,
                                                )
                                            },
                                        )
                                        ExportPreferences.save(columnConfig)
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Column list
                            columnConfig.columns.forEachIndexed { index, column ->
                                ExportColumnRow(
                                    column = column,
                                    index = index,
                                    isFirst = index == 0,
                                    isLast = index == columnConfig.columns.lastIndex,
                                    onToggleEnabled = { enabled ->
                                        val updated = when (column) {
                                            is ExportColumn.Field -> column.copy(enabled = enabled)
                                            is ExportColumn.Spacer -> column // spacers are always "enabled"
                                        }
                                        columnConfig = ExportColumnConfig(
                                            columns = columnConfig.columns.toMutableList().apply {
                                                set(index, updated)
                                            },
                                        )
                                        ExportPreferences.save(columnConfig)
                                    },
                                    onMoveUp = {
                                        columnConfig = ExportColumnConfig(
                                            columns = columnConfig.columns.toMutableList().apply {
                                                val item = removeAt(index)
                                                add(index - 1, item)
                                            },
                                        )
                                        ExportPreferences.save(columnConfig)
                                    },
                                    onMoveDown = {
                                        columnConfig = ExportColumnConfig(
                                            columns = columnConfig.columns.toMutableList().apply {
                                                val item = removeAt(index)
                                                add(index + 1, item)
                                            },
                                        )
                                        ExportPreferences.save(columnConfig)
                                    },
                                    onRemove = if (column is ExportColumn.Spacer) {
                                        {
                                            columnConfig = ExportColumnConfig(
                                                columns = columnConfig.columns.toMutableList().apply {
                                                    removeAt(index)
                                                },
                                            )
                                            ExportPreferences.save(columnConfig)
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Add Empty Column button
                            CwSecondaryButton(
                                text = "Add Empty Column",
                                onClick = {
                                    val spacer = ExportColumn.Spacer(
                                        id = "spacer-${System.currentTimeMillis()}",
                                    )
                                    columnConfig = ExportColumnConfig(
                                        columns = columnConfig.columns + spacer,
                                    )
                                    ExportPreferences.save(columnConfig)
                                },
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

/**
 * A single row in the Export Columns configuration list.
 *
 * Displays a checkbox (for [ExportColumn.Field] columns), the column name,
 * up/down reorder buttons, and a remove button (for [ExportColumn.Spacer] columns).
 */
@Composable
private fun ExportColumnRow(
    column: ExportColumn,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Checkbox — only meaningful for Field columns
        when (column) {
            is ExportColumn.Field -> {
                Checkbox(
                    checked = column.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = BrandGreen,
                        uncheckedColor = LightGray,
                        checkmarkColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    modifier = Modifier.size(40.dp),
                )
            }
            is ExportColumn.Spacer -> {
                // Reserve the same space so labels stay aligned
                Spacer(modifier = Modifier.size(40.dp))
            }
        }

        // Column name label
        when (column) {
            is ExportColumn.Field -> {
                Text(
                    text = column.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (column.enabled) DeepInk else SoftGray,
                    modifier = Modifier.weight(1f),
                )
            }
            is ExportColumn.Spacer -> {
                Text(
                    text = column.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = SoftGray,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Move up button
        IconButton(
            onClick = onMoveUp,
            enabled = !isFirst,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Move up",
                tint = if (!isFirst) SoftGray else LightGray,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Move down button
        IconButton(
            onClick = onMoveDown,
            enabled = !isLast,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Move down",
                tint = if (!isLast) SoftGray else LightGray,
                modifier = Modifier.size(20.dp),
            )
        }

        // Remove button (spacers only)
        if (onRemove != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = SoftGray,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            // Reserve space so rows align when some have remove buttons
            Spacer(modifier = Modifier.width(36.dp))
        }
    }
}
