package com.soneso.smartdemo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soneso.smartdemo.util.describeSignerType
import com.soneso.smartdemo.util.formatSignerForDisplay
import com.soneso.smartdemo.util.signerTypeColor
import com.soneso.stellar.sdk.smartaccount.core.SmartAccountSigner

/**
 * Returns the badge color for a signer, derived from its display type.
 *
 * Shared by [SignerIdentityChip] (badge background) and the signer cards
 * (tinted card container).
 */
fun signerChipColor(signer: SmartAccountSigner): Color =
    signerTypeColor(describeSignerType(signer))

/**
 * Signer identity block: colored type badge, optional "(on-chain)" badge, and the
 * monospace identifier with an optional subline.
 *
 * The single source for rendering a signer's identity. Used by the Signers-section
 * cards and the weighted-threshold per-signer weight rows so the same signer is
 * presented identically everywhere.
 *
 * @param signer The signer to render.
 * @param modifier Layout modifier for the enclosing row.
 * @param showOnChainBadge When true, a green "(on-chain)" badge is shown after the
 *   type badge (edit mode, for entries that exist on-chain).
 * @param subline Optional secondary line rendered under the identifier (e.g. the
 *   on-chain weight in the weighted-threshold edit rows).
 */
@Composable
fun SignerIdentityChip(
    signer: SmartAccountSigner,
    modifier: Modifier = Modifier,
    showOnChainBadge: Boolean = false,
    subline: String? = null
) {
    val displayInfo = formatSignerForDisplay(signer)
    val chipColor = signerChipColor(signer)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        // Type badge
        Surface(
            color = chipColor,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = displayInfo.type,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }

        // On-chain badge
        if (showOnChainBadge) {
            Surface(
                color = Color(0xFF4CAF50),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "(on-chain)",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Identifier with optional subline
        Column {
            Text(
                text = displayInfo.display,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subline != null) {
                Text(
                    text = subline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
