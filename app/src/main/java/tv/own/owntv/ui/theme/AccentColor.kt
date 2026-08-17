package tv.own.owntv.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import tv.own.owntv.R

/**
 * Material You-style accent presets. The default TEAL slot is Great White's aqua so a fresh
 * installation immediately matches the GWS identity while the other user-selectable accents remain.
 */
enum class AccentColor(
    @param:StringRes val labelRes: Int,
    private val primaryDark: Color,
    private val onPrimaryDark: Color,
    private val primaryContainerDark: Color,
    private val onPrimaryContainerDark: Color,
    private val primaryLight: Color,
    private val onPrimaryLight: Color,
    private val primaryContainerLight: Color,
    private val onPrimaryContainerLight: Color,
) {
    TEAL(
        R.string.settings_accent_teal,
        primaryDark = Color(0xFF2DE2C4), onPrimaryDark = Color(0xFF002F29),
        primaryContainerDark = Color(0xFF07554A), onPrimaryContainerDark = Color(0xFF8CFFE9),
        primaryLight = Color(0xFF087A68), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFF9FFFEA), onPrimaryContainerLight = Color(0xFF00201B),
    ),
    BLUE(
        R.string.settings_accent_blue,
        primaryDark = Color(0xFF6FB0FF), onPrimaryDark = Color(0xFF00315C),
        primaryContainerDark = Color(0xFF134A7C), onPrimaryContainerDark = Color(0xFFD3E4FF),
        primaryLight = Color(0xFF1565C0), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFD6E3FF), onPrimaryContainerLight = Color(0xFF001C3A),
    ),
    VIOLET(
        R.string.settings_accent_violet,
        primaryDark = Color(0xFFCBBEFF), onPrimaryDark = Color(0xFF312170),
        primaryContainerDark = Color(0xFF483A88), onPrimaryContainerDark = Color(0xFFE7DEFF),
        primaryLight = Color(0xFF5B45C9), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFE5DEFF), onPrimaryContainerLight = Color(0xFF190066),
    ),
    GREEN(
        R.string.settings_accent_green,
        primaryDark = Color(0xFF6FDB94), onPrimaryDark = Color(0xFF00391C),
        primaryContainerDark = Color(0xFF1F5135), onPrimaryContainerDark = Color(0xFF8BF8AF),
        primaryLight = Color(0xFF1B6B3F), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFA6F2C0), onPrimaryContainerLight = Color(0xFF00210F),
    ),
    AMBER(
        R.string.settings_accent_amber,
        primaryDark = Color(0xFFFFB95C), onPrimaryDark = Color(0xFF452B00),
        primaryContainerDark = Color(0xFF624000), onPrimaryContainerDark = Color(0xFFFFDDB3),
        primaryLight = Color(0xFF8A5100), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFDDB3), onPrimaryContainerLight = Color(0xFF2C1600),
    );

    fun primary(isDark: Boolean) = if (isDark) primaryDark else primaryLight
    fun onPrimary(isDark: Boolean) = if (isDark) onPrimaryDark else onPrimaryLight
    fun primaryContainer(isDark: Boolean) = if (isDark) primaryContainerDark else primaryContainerLight
    fun onPrimaryContainer(isDark: Boolean) = if (isDark) onPrimaryContainerDark else onPrimaryContainerLight
}
