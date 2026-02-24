package tech.carbonworks.snc.batchreferralparser.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.carbonworks.snc.batchreferralparser.ui.theme.CleanWhite
import tech.carbonworks.snc.batchreferralparser.ui.theme.DeepInk
import tech.carbonworks.snc.batchreferralparser.ui.theme.LightGray
import tech.carbonworks.snc.batchreferralparser.ui.theme.SkyBlue
import tech.carbonworks.snc.batchreferralparser.ui.theme.SoftGray

/**
 * Primary action button with DeepInk background and CleanWhite text.
 */
@Composable
fun CwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = DeepInk,
            contentColor = CleanWhite,
            disabledContainerColor = LightGray,
            disabledContentColor = SoftGray,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}

/**
 * Secondary action button with outlined style.
 */
@Composable
fun CwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DeepInk,
            disabledContentColor = SoftGray,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}

/**
 * Accent button with SkyBlue background for interactive/highlighted actions.
 */
@Composable
fun CwAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = SkyBlue,
            contentColor = CleanWhite,
            disabledContainerColor = LightGray,
            disabledContentColor = SoftGray,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(text)
    }
}
