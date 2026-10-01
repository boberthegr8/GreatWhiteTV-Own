package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * GWS Wave lockup used on the TV-first home experience.
 *
 * The mark intentionally stays asset-light so the fork does not inherit or imitate another player's
 * branding. It uses the active theme accent and the existing play glyph while presenting a distinct
 * GWS Wave wordmark.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    textSize: Int = 26,
) {
    val colors = OwnTVTheme.colors
    val gws = stringResource(R.string.wave_brand_gws)
    val wave = stringResource(R.string.wave_brand_wave)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val markShape = RoundedCornerShape(percent = 30)
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(markSize.dp)
                .clip(markShape)
                .background(colors.card)
                .border(2.dp, colors.primary, markShape),
            contentAlignment = Alignment.Center,
        ) {
            OwnTVIcon(
                icon = OwnTVIcon.PLAY,
                tint = colors.primary,
                filled = true,
                modifier = Modifier
                    .padding(start = (markSize * 0.06f).dp)
                    .size((markSize * 0.5f).dp),
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(gws)
                }
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    ),
                ) {
                    append(wave)
                }
            },
            fontSize = textSize.sp,
        )
    }
}
