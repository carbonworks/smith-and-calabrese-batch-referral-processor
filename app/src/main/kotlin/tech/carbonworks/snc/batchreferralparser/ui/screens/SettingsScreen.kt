package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
 * Returns a stable unique key for an [ExportColumn], suitable for use as a
 * LazyColumn item key.
 */
private fun ExportColumn.stableKey(): String = when (this) {
    is ExportColumn.Field -> "field-$fieldId"
    is ExportColumn.Spacer -> "spacer-$id"
}

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

                            // Preset buttons and Insert Empty Column toolbar row
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
                                CwSecondaryButton(
                                    text = "Insert Empty Column",
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
                                CwSecondaryButton(
                                    text = "Reset",
                                    onClick = {
                                        ExportPreferences.reset()
                                        columnConfig = ExportColumnConfig.default()
                                    },
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Reorderable column list in a fixed-height container
                            ExportColumnReorderableList(
                                columnConfig = columnConfig,
                                onColumnConfigChanged = { newConfig ->
                                    columnConfig = newConfig
                                    ExportPreferences.save(newConfig)
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
 * A reorderable LazyColumn that displays the export column configuration.
 *
 * Supports drag-and-drop reordering via drag handles, as well as the existing
 * up/down arrow button reordering. Lives inside a fixed-height container to
 * avoid gesture conflicts with the outer Settings scroll.
 */
@Composable
private fun ExportColumnReorderableList(
    columnConfig: ExportColumnConfig,
    onColumnConfigChanged: (ExportColumnConfig) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        val updatedColumns = columnConfig.columns.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onColumnConfigChanged(ExportColumnConfig(columns = updatedColumns))
    }

    // Fixed-height container prevents nested scroll conflicts with the
    // outer Settings verticalScroll.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(
                items = columnConfig.columns,
                key = { _, column -> column.stableKey() },
            ) { index, column ->
                ReorderableItem(
                    state = reorderableState,
                    key = column.stableKey(),
                ) { isDragging ->
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shadowElevation = elevation,
                        tonalElevation = if (isDragging) 2.dp else 0.dp,
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDragging) {
                            WarmWhite
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    ) {
                        ExportColumnRow(
                            column = column,
                            index = index,
                            isFirst = index == 0,
                            isLast = index == columnConfig.columns.lastIndex,
                            onToggleEnabled = { enabled ->
                                val updated = when (column) {
                                    is ExportColumn.Field -> column.copy(enabled = enabled)
                                    is ExportColumn.Spacer -> column
                                }
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            set(index, updated)
                                        },
                                    ),
                                )
                            },
                            onMoveUp = {
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index - 1, item)
                                        },
                                    ),
                                )
                            },
                            onMoveDown = {
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index + 1, item)
                                        },
                                    ),
                                )
                            },
                            onMoveToTop = {
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(0, item)
                                        },
                                    ),
                                )
                            },
                            onMoveToBottom = {
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(item)
                                        },
                                    ),
                                )
                            },
                            onInsertSpacerAbove = {
                                val spacer = ExportColumn.Spacer(
                                    id = "spacer-${System.currentTimeMillis()}",
                                )
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            add(index, spacer)
                                        },
                                    ),
                                )
                            },
                            onInsertSpacerBelow = {
                                val spacer = ExportColumn.Spacer(
                                    id = "spacer-${System.currentTimeMillis()}",
                                )
                                onColumnConfigChanged(
                                    ExportColumnConfig(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            add(index + 1, spacer)
                                        },
                                    ),
                                )
                            },
                            onRemove = if (column is ExportColumn.Spacer) {
                                {
                                    onColumnConfigChanged(
                                        ExportColumnConfig(
                                            columns = columnConfig.columns.toMutableList().apply {
                                                removeAt(index)
                                            },
                                        ),
                                    )
                                }
                            } else {
                                null
                            },
                            dragModifier = Modifier.draggableHandle(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row in the Export Columns configuration list.
 *
 * Displays a drag handle, a checkbox (for [ExportColumn.Field] columns) or
 * inline close button (for [ExportColumn.Spacer] columns), the column name,
 * up/down reorder buttons, and a three-dot overflow menu with additional
 * actions (Move to Top/Bottom, Insert Spacer Above/Below, Remove).
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
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onInsertSpacerAbove: () -> Unit,
    onInsertSpacerBelow: () -> Unit,
    onRemove: (() -> Unit)?,
    dragModifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drag handle (6-dot grip icon)
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = SoftGray,
            modifier = dragModifier
                .size(24.dp)
                .padding(end = 4.dp),
        )

        // Checkbox for Field columns; inline Close button for Spacer columns
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
                // Inline close button for single-click spacer removal
                IconButton(
                    onClick = { onRemove?.invoke() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove spacer",
                        tint = SoftGray,
                        modifier = Modifier.size(18.dp),
                    )
                }
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

        // Reserve space for alignment
        Spacer(modifier = Modifier.width(36.dp))

        Spacer(modifier = Modifier.width(4.dp))

        // Overflow menu (three-dot button)
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = SoftGray,
                    modifier = Modifier.size(20.dp),
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                // Move to Top
                DropdownMenuItem(
                    text = { Text("Move to Top") },
                    onClick = {
                        menuExpanded = false
                        onMoveToTop()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignTop,
                            contentDescription = null,
                        )
                    },
                    enabled = !isFirst,
                )

                // Move to Bottom
                DropdownMenuItem(
                    text = { Text("Move to Bottom") },
                    onClick = {
                        menuExpanded = false
                        onMoveToBottom()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                        )
                    },
                    enabled = !isLast,
                )

                HorizontalDivider()

                // Insert Spacer Above
                DropdownMenuItem(
                    text = { Text("Insert Spacer Above") },
                    onClick = {
                        menuExpanded = false
                        onInsertSpacerAbove()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                )

                // Insert Spacer Below
                DropdownMenuItem(
                    text = { Text("Insert Spacer Below") },
                    onClick = {
                        menuExpanded = false
                        onInsertSpacerBelow()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                )

                // Remove option (spacer rows only)
                if (onRemove != null) {
                    HorizontalDivider()

                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}
