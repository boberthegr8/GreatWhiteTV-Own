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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection

/**
 * Near-full-screen Live TV EPG drawer used while the current channel keeps playing behind it.
 * Live TV owns the EPG; this drawer is the appliance-style collapsible in-player layer, leaving
 * a strip of video visible on the trailing edge.
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
        val panelWidth = maxWidth * 0.88f
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
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key.horizontalDirection(layoutDirection) == HorizontalDirection.START
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
                .clip(RoundedCornerShape(0.dp, 22.dp, 22.dp, 0.dp))
                .background(Color(0xF207111C)),
        ) {
            content()
        }
    }
}
