package tv.own.owntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Near-full-screen Guide drawer used while Live TV remains playing behind the shell.
 * The full Guide destination still exists for normal navigation; this wrapper is specifically
 * the appliance-style in-player layer, leaving a strip of video visible on the trailing edge.
 */
@Composable
fun GuideDrawerOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    var revealed by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWidth = maxWidth * 0.80f
        val hiddenOffset = if (layoutDirection == LayoutDirection.Ltr) -panelWidth else panelWidth
        val slideOffset by animateDpAsState(
            targetValue = if (revealed) 0.dp else hiddenOffset,
            animationSpec = tween(durationMillis = 210),
        )

        LaunchedEffect(Unit) { revealed = true }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = slideOffset)
                .fillMaxHeight()
                .width(panelWidth)
                .clip(RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp))
                .background(Color(0xF207111C)),
        ) {
            content()
        }
    }
}
