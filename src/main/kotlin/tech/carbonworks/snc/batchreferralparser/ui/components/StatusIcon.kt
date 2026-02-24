package tech.carbonworks.snc.batchreferralparser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.carbonworks.snc.batchreferralparser.ui.theme.CleanWhite
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.PaperTan
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftTeal

/**
 * Processing status for a single file in the batch pipeline.
 */
enum class FileStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    ERROR,
}

/**
 * Circular status icon indicating the processing state of a file.
 *
 * - PENDING: LightGray circle with dash
 * - PROCESSING: PaperTan circle with ellipsis
 * - SUCCESS: SoftTeal circle with checkmark
 * - ERROR: Red circle with X
 */
@Composable
fun StatusIcon(
    status: FileStatus,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (status) {
        FileStatus.PENDING -> LightGray
        FileStatus.PROCESSING -> PaperTan
        FileStatus.SUCCESS -> SoftTeal
        FileStatus.ERROR -> Color(0xFFE53E3E)
    }
    val symbol = when (status) {
        FileStatus.PENDING -> "\u2013"   // en dash
        FileStatus.PROCESSING -> "\u2026" // ellipsis
        FileStatus.SUCCESS -> "\u2713"    // checkmark
        FileStatus.ERROR -> "\u2717"      // X
    }
    val textColor = when (status) {
        FileStatus.PENDING -> Color(0xFF718096)
        else -> CleanWhite
    }

    Box(
        modifier = modifier
            .size(22.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 14.sp,
        )
    }
}
