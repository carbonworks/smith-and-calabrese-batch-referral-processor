package tech.carbonworks.snc.batchreferralparser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary palette
val DeepInk = Color(0xFF2D3748)
val SoftGray = Color(0xFF718096)
val LightGray = Color(0xFFE2E8F0)
val WarmWhite = Color(0xFFFFFAF5)
val CleanWhite = Color(0xFFFFFFFF)

// Accent colors
val SkyBlue = Color(0xFF4A9FD4)
val SoftTeal = Color(0xFF38B2AC)
val PaperTan = Color(0xFFD4A574)

private val CarbonWorksColorScheme = lightColorScheme(
    primary = DeepInk,
    onPrimary = CleanWhite,
    secondary = SkyBlue,
    onSecondary = CleanWhite,
    tertiary = PaperTan,
    background = WarmWhite,
    onBackground = DeepInk,
    surface = CleanWhite,
    onSurface = DeepInk,
    surfaceVariant = LightGray,
    onSurfaceVariant = SoftGray,
    outline = LightGray,
)

@Composable
fun CarbonWorksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarbonWorksColorScheme,
        content = content,
    )
}
