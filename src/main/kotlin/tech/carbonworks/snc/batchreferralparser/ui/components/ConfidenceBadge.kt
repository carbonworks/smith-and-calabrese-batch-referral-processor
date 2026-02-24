package tech.carbonworks.snc.batchreferralparser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.extraction.Confidence
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandGreen
import tech.carbonworks.snc.batchreferralparser.ui.theme.BrandOrange
import tech.carbonworks.snc.batchreferralparser.ui.theme.CleanWhite

/**
 * Colored badge displaying a confidence level (HIGH, MEDIUM, or LOW).
 *
 * Colors:
 * - HIGH: SoftTeal background
 * - MEDIUM: PaperTan background
 * - LOW: Muted red background
 */
@Composable
fun ConfidenceBadge(
    confidence: Confidence,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (confidence) {
        Confidence.HIGH -> BrandGreen
        Confidence.MEDIUM -> BrandOrange
        Confidence.LOW -> Color(0xFFE53E3E)
    }
    val textColor = CleanWhite

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = confidence.name,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
        )
    }
}
