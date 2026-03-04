package tech.carbonworks.snc.batchreferralparser

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import tech.carbonworks.snc.batchreferralparser.ui.screens.FileProcessingState
import tech.carbonworks.snc.batchreferralparser.ui.screens.HelpScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.MainScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.ProcessedReferral
import tech.carbonworks.snc.batchreferralparser.ui.screens.ProcessingScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.ResultsScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.ExportSettingsScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.SettingsScreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.CarbonWorksTheme
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite
import java.io.File

/**
 * Application screens for the batch processing workflow.
 */
enum class Screen {
    FILE_SELECTION,
    PROCESSING,
    RESULTS,
    HELP,
    SETTINGS,
    EXPORT_SETTINGS,
}

/**
 * Load the CarbonWorks origami bird icon from classpath resources.
 * Returns null if the icon has not been generated yet (run: ./gradlew :app:generateIcons).
 */
private fun loadAppIcon(): Painter? =
    try {
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("icon.png")
            ?.use { BitmapPainter(loadImageBitmap(it)) }
    } catch (_: Exception) {
        null
    }

fun main() = application {
    val appIcon = loadAppIcon()

    Window(
        onCloseRequest = ::exitApplication,
        title = "PDF Authorization Processor",
        icon = appIcon,
        state = rememberWindowState(width = 1100.dp, height = 700.dp),
    ) {
        window.minimumSize = Dimension(600, 400)
        App(window)
    }
}

@Composable
fun App(window: java.awt.Window? = null) {
    // All app state hoisted to App() level
    var currentScreen by remember { mutableStateOf(Screen.FILE_SELECTION) }
    var previousScreen by remember { mutableStateOf(Screen.FILE_SELECTION) }
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileStates by remember { mutableStateOf<List<FileProcessingState>>(emptyList()) }
    var processingResults by remember { mutableStateOf<List<ProcessedReferral>>(emptyList()) }

    CarbonWorksTheme {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmWhite),
        ) {
            Crossfade(
                targetState = currentScreen,
                animationSpec = tween(300),
                label = "screen-transition",
            ) { screen ->
                when (screen) {
                    Screen.FILE_SELECTION -> {
                        MainScreen(
                            files = selectedFiles,
                            onFilesChanged = { selectedFiles = it },
                            onProcess = {
                                fileStates = selectedFiles.map { FileProcessingState(it) }
                                processingResults = emptyList()
                                currentScreen = Screen.PROCESSING
                            },
                            onHelp = {
                                previousScreen = currentScreen
                                currentScreen = Screen.HELP
                            },
                            onSettings = {
                                previousScreen = currentScreen
                                currentScreen = Screen.SETTINGS
                            },
                            window = window,
                        )
                    }

                    Screen.PROCESSING -> {
                        ProcessingScreen(
                            files = selectedFiles,
                            fileStates = fileStates,
                            onFileStateUpdate = { index, state ->
                                fileStates = fileStates.toMutableList().also {
                                    it[index] = state
                                }
                            },
                            onComplete = { results ->
                                println("[Nav] Processing complete, received ${results.size} result(s)")
                                println("[Nav]   Success: ${results.count { it.fields != null }}, Errors: ${results.count { it.error != null }}")
                                processingResults = results
                                println("[Nav] Set processingResults (size=${processingResults.size}), navigating to RESULTS")
                                currentScreen = Screen.RESULTS
                            },
                        )
                    }

                    Screen.RESULTS -> {
                        ResultsScreen(
                            results = processingResults,
                            onStartOver = {
                                selectedFiles = emptyList()
                                fileStates = emptyList()
                                processingResults = emptyList()
                                currentScreen = Screen.FILE_SELECTION
                            },
                            onNavigateToHelp = {
                                previousScreen = currentScreen
                                currentScreen = Screen.HELP
                            },
                            onNavigateToExportSettings = {
                                previousScreen = currentScreen
                                currentScreen = Screen.EXPORT_SETTINGS
                            },
                        )
                    }

                    Screen.HELP -> {
                        HelpScreen(
                            onBack = { currentScreen = previousScreen },
                        )
                    }

                    Screen.SETTINGS -> {
                        SettingsScreen(
                            onBack = { currentScreen = previousScreen },
                            onNavigateToExportSettings = {
                                previousScreen = currentScreen
                                currentScreen = Screen.EXPORT_SETTINGS
                            },
                        )
                    }

                    Screen.EXPORT_SETTINGS -> {
                        ExportSettingsScreen(
                            onBack = { currentScreen = previousScreen },
                        )
                    }
                }
            }
        }
    }
}
