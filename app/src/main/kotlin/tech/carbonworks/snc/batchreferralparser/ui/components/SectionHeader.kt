package tech.carbonworks.snc.batchreferralparser.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk

/**
 * Consistent section heading styled with DeepInk color and titleMedium typography.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = DeepInk,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
