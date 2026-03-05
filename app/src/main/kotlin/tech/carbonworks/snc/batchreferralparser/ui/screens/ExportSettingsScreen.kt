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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import tech.carbonworks.snc.batchreferralparser.output.DEFAULT_FIELD_ORDER
import tech.carbonworks.snc.batchreferralparser.output.ExportColumn
import tech.carbonworks.snc.batchreferralparser.output.ExportColumnConfig
import tech.carbonworks.snc.batchreferralparser.output.ExportPreferences
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite

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
 * Export Settings screen: allows the user to configure which columns appear
 * in the exported spreadsheet and their order.
 *
 * Provides preset buttons (All Fields, Essential Only, Reset) and a
 * drag-and-drop reorderable column list with checkboxes, overflow menus,
 * and spacer insertion.
 *
 * @param onBack callback to return to the Settings screen
 */
@Composable
fun ExportSettingsScreen(
    onBack: () -> Unit,
) {
    var columnConfig by remember { mutableStateOf(ExportPreferences.load()) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite)
            .padding(32.dp),
    ) {
        // Header
        Text(
            text = "Export Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = DeepInk,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Choose which columns appear in the exported spreadsheet and their order",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftGray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Expand services checkbox — negative start padding compensates for
        // the Checkbox's built-in touch-target padding so the row aligns
        // flush with the preset buttons and column list above/below it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (-12).dp),
        ) {
            Checkbox(
                checked = columnConfig.expandServices,
                onCheckedChange = { checked ->
                    println("[ExportSettings] Expand services changed to: $checked")
                    columnConfig = columnConfig.copy(expandServices = checked)
                    ExportPreferences.save(columnConfig)
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandGreen,
                    uncheckedColor = LightGray,
                    checkmarkColor = androidx.compose.ui.graphics.Color.White,
                ),
            )
            Text(
                text = "Place each service on its own row (duplicate other fields)",
                style = MaterialTheme.typography.bodyMedium,
                color = DeepInk,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset buttons toolbar row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CwSecondaryButton(
                text = "All Fields",
                onClick = {
                    println("[ExportSettings] Preset applied: All Fields")
                    columnConfig = ExportColumnConfig.default().copy(
                        expandServices = columnConfig.expandServices,
                    )
                    ExportPreferences.save(columnConfig)
                },
            )
            CwSecondaryButton(
                text = "Essential Only",
                onClick = {
                    println("[ExportSettings] Preset applied: Essential Only")
                    columnConfig = ExportColumnConfig(
                        columns = DEFAULT_FIELD_ORDER.map { (fieldId, displayName) ->
                            ExportColumn.Field(
                                fieldId = fieldId,
                                displayName = displayName,
                                enabled = fieldId in ESSENTIAL_FIELD_IDS,
                            )
                        },
                        expandServices = columnConfig.expandServices,
                    )
                    ExportPreferences.save(columnConfig)
                },
            )
            CwSecondaryButton(
                text = "Reset",
                onClick = { showResetDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Reorderable column list filling available space
        Box(modifier = Modifier.weight(1f)) {
            ExportColumnReorderableList(
                columnConfig = columnConfig,
                onColumnConfigChanged = { newConfig ->
                    columnConfig = newConfig
                    ExportPreferences.save(newConfig)
                },
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

    // Reset confirmation dialog
    if (showResetDialog) {
        var confirmationText by remember { mutableStateOf("") }
        val isConfirmEnabled = confirmationText.equals("reset", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Export Columns",
                    color = DeepInk,
                )
            },
            text = {
                Column {
                    Text(
                        text = "This will restore all columns to their default configuration. " +
                            "Any custom ordering, visibility changes, and spacers will be lost.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepInk,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Type \"reset\" to confirm:",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGray,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmationText,
                        onValueChange = { confirmationText = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "reset",
                                color = LightGray,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        println("[ExportSettings] Reset confirmed — restoring defaults")
                        ExportPreferences.reset()
                        columnConfig = ExportColumnConfig.default()
                        showResetDialog = false
                    },
                    enabled = isConfirmEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = BrandGreen,
                    ),
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

/**
 * A reorderable LazyColumn that displays the export column configuration.
 *
 * Supports drag-and-drop reordering via drag handles, as well as the existing
 * up/down arrow button reordering.
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
        onColumnConfigChanged(columnConfig.copy(columns = updatedColumns))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
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
                                    columnConfig.copy(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            set(index, updated)
                                        },
                                    ),
                                )
                            },
                            onMoveUp = {
                                onColumnConfigChanged(
                                    columnConfig.copy(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index - 1, item)
                                        },
                                    ),
                                )
                            },
                            onMoveDown = {
                                onColumnConfigChanged(
                                    columnConfig.copy(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(index + 1, item)
                                        },
                                    ),
                                )
                            },
                            onMoveToTop = {
                                onColumnConfigChanged(
                                    columnConfig.copy(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            val item = removeAt(index)
                                            add(0, item)
                                        },
                                    ),
                                )
                            },
                            onMoveToBottom = {
                                onColumnConfigChanged(
                                    columnConfig.copy(
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
                                    columnConfig.copy(
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
                                    columnConfig.copy(
                                        columns = columnConfig.columns.toMutableList().apply {
                                            add(index + 1, spacer)
                                        },
                                    ),
                                )
                            },
                            onRemove = if (column is ExportColumn.Spacer) {
                                {
                                    onColumnConfigChanged(
                                        columnConfig.copy(
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

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(lazyListState),
        )
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
