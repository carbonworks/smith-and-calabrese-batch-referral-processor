package tech.carbonworks.snc.batchreferralparser.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.ui.components.CwCard
import tech.carbonworks.snc.batchreferralparser.ui.components.CwPrimaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.CwSecondaryButton
import tech.carbonworks.snc.batchreferralparser.ui.components.FilePathText
import tech.carbonworks.snc.batchreferralparser.ui.components.SectionHeader
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.CleanWhite
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SkyBlue
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/** Maximum number of PDFs allowed in a single batch. */
private const val MAX_FILES = 50

/** Preferences key for the last-used file picker directory. */
private const val PREF_LAST_DIRECTORY = "lastDirectory"

/** User preferences node for persisting application settings. */
private val prefs: Preferences =
    Preferences.userRoot().node("tech/carbonworks/snc/batchreferralparser")

/**
 * Load the last-used directory from preferences.
 *
 * @return the saved directory if it exists and is valid, or null otherwise
 */
private fun loadLastDirectory(): File? {
    val path = prefs.get(PREF_LAST_DIRECTORY, null) ?: return null
    val dir = File(path)
    return if (dir.isDirectory) dir else null
}

/**
 * Save a directory path to preferences for next session.
 */
private fun saveLastDirectory(dir: File) {
    prefs.put(PREF_LAST_DIRECTORY, dir.absolutePath)
}

/**
 * File selection screen: the entry point of the batch processing workflow.
 *
 * Provides a drag-and-drop zone for PDF files, a file picker button, and a file list
 * with remove actions. Enforces a 50-file limit.
 *
 * @param files the current list of selected PDF files
 * @param onFilesChanged callback when files are added or removed
 * @param onProcess callback to begin processing the selected files
 * @param window the AWT window reference for drag-and-drop registration
 */
@Composable
fun MainScreen(
    files: List<File>,
    onFilesChanged: (List<File>) -> Unit,
    onProcess: () -> Unit,
    onHelp: () -> Unit = {},
    onSettings: () -> Unit = {},
    window: java.awt.Window?,
) {
    var isDragOver by remember { mutableStateOf(false) }
    var limitMessage by remember { mutableStateOf<String?>(null) }

    // Use refs to always read the latest values inside the AWT callback
    // without re-registering the DropTarget on every recomposition.
    val filesRef = remember { mutableStateOf(files) }
    filesRef.value = files
    val onFilesChangedRef = remember { mutableStateOf(onFilesChanged) }
    onFilesChangedRef.value = onFilesChanged

    // Register AWT drag-and-drop handler on the window and all child components.
    // In Compose Desktop, the rendering surface (SkiaLayer / ComposePanel) intercepts
    // drag events before they reach the top-level window. Setting the DropTarget only
    // on the window silently fails. The fix is to walk the AWT component tree and
    // attach the same DropTarget to every component, ensuring drops are caught
    // regardless of which layer the OS delivers the event to.
    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose {}

        val listener = object : DropTargetAdapter() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                isDragOver = true
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
            }

            override fun dragExit(dte: DropTargetEvent) {
                isDragOver = false
            }

            override fun drop(dtde: DropTargetDropEvent) {
                isDragOver = false
                dtde.acceptDrop(DnDConstants.ACTION_COPY)

                try {
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val droppedFiles = transferable.getTransferData(
                            DataFlavor.javaFileListFlavor
                        ) as List<File>

                        val pdfFiles = droppedFiles.filter {
                            it.extension.equals("pdf", ignoreCase = true)
                        }

                        println("[FileSelection] Drag-drop: ${droppedFiles.size} file(s) dropped, ${pdfFiles.size} PDF(s) accepted")
                        addFilesWithLimit(filesRef.value, pdfFiles, onFilesChangedRef.value) { msg ->
                            limitMessage = msg
                        }

                        // Remember the directory of the dropped files
                        pdfFiles.firstOrNull()?.parentFile?.let { dir ->
                            saveLastDirectory(dir)
                        }
                    }
                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    println("[FileSelection] Drag-drop error: ${e.message}")
                    dtde.dropComplete(false)
                }
            }
        }

        // Collect all components in the window hierarchy.
        val allComponents = mutableListOf<Component>()
        fun collectComponents(container: Container) {
            allComponents.add(container)
            for (child in container.components) {
                allComponents.add(child)
                if (child is Container) {
                    collectComponents(child)
                }
            }
        }
        collectComponents(window)

        // Save original drop targets so we can restore them on dispose.
        val originalTargets = allComponents.map { it to it.dropTarget }

        // Set our drop target on every component.
        for (component in allComponents) {
            component.dropTarget = DropTarget(
                component,
                DnDConstants.ACTION_COPY_OR_MOVE,
                listener,
            )
        }

        onDispose {
            // Restore original drop targets.
            for ((component, original) in originalTargets) {
                component.dropTarget = original
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmWhite),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Batch Authorization Processor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = DeepInk,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gather authorization information from Maryland Disability Determination Services authorization forms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = BrandGreen,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(
                    onClick = onHelp,
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

        Spacer(modifier = Modifier.height(32.dp))

        // Drop zone
        val dropBorderColor = if (isDragOver) SkyBlue else LightGray
        val dropBackground = if (isDragOver) SkyBlue.copy(alpha = 0.05f) else CleanWhite

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    border = BorderStroke(
                        width = 2.dp,
                        color = dropBorderColor,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                .background(dropBackground)
                .clickable { openFilePicker(files, onFilesChanged) { msg -> limitMessage = msg } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isDragOver) "Drop PDF files here" else "Drag & drop PDF files here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDragOver) SkyBlue else SoftGray,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "or click to browse",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Limit warning message
        limitMessage?.let { msg ->
            Text(
                text = msg,
                color = SoftGray,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // File list section
        if (files.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(text = "Selected Files (${files.size})")
                CwSecondaryButton(
                    text = "Clear All",
                    onClick = {
                        println("[FileSelection] Cleared all ${files.size} file(s)")
                        onFilesChanged(emptyList())
                        limitMessage = null
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            CwCard(
                modifier = Modifier.weight(1f),
            ) {
                val lazyListState = rememberLazyListState()
                Box {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FilePathText(
                                    path = file.name,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = formatFileSize(file.length()),
                                    fontSize = 12.sp,
                                    color = SoftGray,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        onFilesChanged(files - file)
                                        if (files.size - 1 < MAX_FILES) {
                                            limitMessage = null
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${file.name}",
                                        tint = SoftGray,
                                        modifier = Modifier.size(16.dp),
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
        } else {
            // Empty state
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No files selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CwSecondaryButton(
                text = "Add Files",
                onClick = { openFilePicker(files, onFilesChanged) { msg -> limitMessage = msg } },
            )
            Spacer(modifier = Modifier.width(12.dp))
            CwPrimaryButton(
                text = "Process ${files.size} File${if (files.size != 1) "s" else ""}",
                onClick = onProcess,
                enabled = files.isNotEmpty(),
            )
        }
    }
    } // end Box
}

/**
 * Open a system file chooser to select PDF files.
 *
 * Restores the last-used directory from preferences and saves the chosen
 * directory after file selection.
 */
private fun openFilePicker(
    currentFiles: List<File>,
    onFilesChanged: (List<File>) -> Unit,
    onLimitMessage: (String?) -> Unit,
) {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    } catch (_: Exception) {
        // Fall back to default L&F
    }

    val chooser = JFileChooser().apply {
        dialogTitle = "Select PDF Authorization Files"
        fileFilter = FileNameExtensionFilter("PDF Files (*.pdf)", "pdf")
        isMultiSelectionEnabled = true
        fileSelectionMode = JFileChooser.FILES_ONLY

        // Restore the last-used directory if available and still valid
        loadLastDirectory()?.let { dir ->
            currentDirectory = dir
        }
    }

    println("[FileSelection] File picker opened")
    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val selected = chooser.selectedFiles.toList()
        println("[FileSelection] File picker: ${selected.size} file(s) selected")
        addFilesWithLimit(currentFiles, selected, onFilesChanged, onLimitMessage)

        // Remember the directory for next time
        selected.firstOrNull()?.parentFile?.let { dir ->
            saveLastDirectory(dir)
        }
    } else {
        println("[FileSelection] File picker cancelled")
    }
}

/**
 * Add files to the current list, enforcing the [MAX_FILES] limit and deduplicating.
 */
private fun addFilesWithLimit(
    currentFiles: List<File>,
    newFiles: List<File>,
    onFilesChanged: (List<File>) -> Unit,
    onLimitMessage: (String?) -> Unit,
) {
    val existingPaths = currentFiles.map { it.absolutePath }.toSet()
    val uniqueNew = newFiles.filter { it.absolutePath !in existingPaths }
    val duplicateCount = newFiles.size - uniqueNew.size

    val available = MAX_FILES - currentFiles.size
    if (available <= 0) {
        println("[FileSelection] Add rejected: limit of $MAX_FILES already reached")
        onLimitMessage("Maximum of $MAX_FILES files reached. Remove files before adding more.")
        return
    }

    val toAdd = uniqueNew.take(available)
    val merged = currentFiles + toAdd

    if (uniqueNew.size > available) {
        val skipped = uniqueNew.size - available
        println("[FileSelection] Added ${toAdd.size} file(s), skipped $skipped (limit: $MAX_FILES), $duplicateCount duplicate(s) ignored")
        onLimitMessage(
            "Added ${toAdd.size} file${if (toAdd.size != 1) "s" else ""}. " +
                "$skipped file${if (skipped != 1) "s" else ""} skipped (limit: $MAX_FILES)."
        )
    } else {
        if (duplicateCount > 0) {
            println("[FileSelection] Added ${toAdd.size} file(s), $duplicateCount duplicate(s) ignored — total: ${merged.size}")
        } else {
            println("[FileSelection] Added ${toAdd.size} file(s) — total: ${merged.size}")
        }
        onLimitMessage(null)
    }

    onFilesChanged(merged)
}

/**
 * Format a byte count as a human-readable file size string.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
