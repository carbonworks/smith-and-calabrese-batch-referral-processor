package tech.carbonworks.snc.batchreferralparser.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk

/**
 * Monospace text display for file paths.
 * Uses a monospace font to ensure consistent alignment and readability.
 */
@Composable
fun FilePathText(
    path: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = path,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = DeepInk,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
