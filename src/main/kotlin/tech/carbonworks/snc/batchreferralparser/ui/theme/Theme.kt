package tech.carbonworks.snc.batchreferralparser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Primary palette
val BrandGreen = Color(0xFF03757A)
val DeepInk = Color(0xFF2D3748)
val SoftGray = Color(0xFF5A6B7E)
val LightGray = Color(0xFFCBD5E0)
val GreenTint = Color(0xFFE6F2F3)
val CleanWhite = Color(0xFFFFFFFF)

// Accent colors
val BrandOrange = Color(0xFFFE904D)
val SoftTeal = Color(0xFF38B2AC)

// Legacy aliases (referenced in screens/components — map to new palette)
val WarmWhite = GreenTint
val SkyBlue = BrandGreen
val PaperTan = BrandOrange

private val CarbonWorksColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = CleanWhite,
    secondary = BrandOrange,
    onSecondary = CleanWhite,
    tertiary = BrandOrange,
    background = GreenTint,
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
