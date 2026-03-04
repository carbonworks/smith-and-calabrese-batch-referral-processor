package tech.carbonworks.snc.batchreferralparser

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.awt.Dimension
import java.io.File
import tech.carbonworks.snc.batchreferralparser.ui.screens.ExportSettingsScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.FileProcessingState
import tech.carbonworks.snc.batchreferralparser.ui.screens.HelpScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.MainScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.ProcessedReferral
import tech.carbonworks.snc.batchreferralparser.ui.screens.ProcessingScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.ResultsScreen
import tech.carbonworks.snc.batchreferralparser.ui.screens.SettingsScreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.CarbonWorksTheme
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite

/**
 * Navigation route constants for the batch processing workflow.
 */
object Routes {
    const val FILE_SELECTION = "file_selection"
    const val PROCESSING = "processing"
    const val RESULTS = "results"
    const val HELP = "help"
    const val SETTINGS = "settings"
    const val EXPORT_SETTINGS = "export_settings"
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
    var selectedFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var fileStates by remember { mutableStateOf<List<FileProcessingState>>(emptyList()) }
    var processingResults by remember { mutableStateOf<List<ProcessedReferral>>(emptyList()) }

    val navController = rememberNavController()

    CarbonWorksTheme {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmWhite),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.FILE_SELECTION,
                enterTransition = { fadeIn(androidx.compose.animation.core.tween(300)) },
                exitTransition = { fadeOut(androidx.compose.animation.core.tween(300)) },
            ) {
                composable(Routes.FILE_SELECTION) {
                    MainScreen(
                        files = selectedFiles,
                        onFilesChanged = { selectedFiles = it },
                        onProcess = {
                            fileStates = selectedFiles.map { FileProcessingState(it) }
                            processingResults = emptyList()
                            navController.navigate(Routes.PROCESSING)
                        },
                        onHelp = {
                            navController.navigate(Routes.HELP)
                        },
                        onSettings = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        window = window,
                    )
                }

                composable(Routes.PROCESSING) {
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
                            navController.navigate(Routes.RESULTS) {
                                popUpTo(Routes.FILE_SELECTION)
                            }
                        },
                    )
                }

                composable(Routes.RESULTS) {
                    ResultsScreen(
                        results = processingResults,
                        onStartOver = {
                            selectedFiles = emptyList()
                            fileStates = emptyList()
                            processingResults = emptyList()
                            navController.popBackStack(Routes.FILE_SELECTION, inclusive = false)
                        },
                        onNavigateToHelp = {
                            navController.navigate(Routes.HELP)
                        },
                        onNavigateToExportSettings = {
                            navController.navigate(Routes.EXPORT_SETTINGS)
                        },
                    )
                }

                composable(Routes.HELP) {
                    HelpScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToExportSettings = {
                            navController.navigate(Routes.EXPORT_SETTINGS)
                        },
                    )
                }

                composable(Routes.EXPORT_SETTINGS) {
                    ExportSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
