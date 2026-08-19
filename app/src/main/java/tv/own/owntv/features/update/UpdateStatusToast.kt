package tv.own.owntv.features.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * Compatibility shim for older shell code that still composes the former automatic startup update
 * card. Great White updates are manual-only now, so this intentionally renders nothing and immediately
 * dismisses itself. The parameters remain until the old shell call site is naturally cleaned up.
 */
@Composable
fun UpdateStatusToast(
    onDone: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onViewChangelog: () -> Unit,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onDone() }
}
