package tech.carbonworks.snc.batchreferralparser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import tech.carbonworks.snc.batchreferralparser.ui.theme.CarbonWorksTheme
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.WarmWhite

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Carbon Works \u2014 PDF Referral Parser",
        state = rememberWindowState(width = 900.dp, height = 600.dp),
    ) {
        App()
    }
}

@Composable
fun App() {
    CarbonWorksTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmWhite),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "S&C Batch Referral Processor",
                color = DeepInk,
                fontSize = 24.sp,
            )
        }
    }
}
