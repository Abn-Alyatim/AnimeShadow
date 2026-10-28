package com.animeshadow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.animeshadow.app.R

object Palette {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0B0B10)
    val Card = Color(0xFF121219)
    val CardHigh = Color(0xFF1A1A24)
    val Divider = Color(0xFF23232E)
    /** Blue-violet from the AS logo slash. */
    val Accent = Color(0xFF7B5CFF)
    val AccentDeep = Color(0xFF5B3BFF)
    val AccentSoft = Color(0x337B5CFF)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB4B4BE)
    val TextMuted = Color(0xFF7E7E8A)
    val Star = Color(0xFFFFC107)
    val Watching = Color(0xFF4FC3F7)
    val Plan = Color(0xFFB39DDB)
    val Completed = Color(0xFF66BB6A)
    val OnHold = Color(0xFFFFB74D)
    val Dropped = Color(0xFFEF5350)
    val Favorite = Color(0xFFFF4D8D)
    /** Blue of the reference app (watched eye, sliders, "cancel"), sampled from refs 03 and 13. */
    val Blue = Color(0xFF0091EA)
    /** Light grey of the reference titles (ref 11). */
    val RefText = Color(0xFFE0E0E0)
    /** v1.3.1 (Madyan): EVERY icon is bright white #FFFFFF at full alpha, like his reference drawer (1.3.0: grey #BDBDBD). */
    val RefIcon = Color(0xFFFFFFFF)
    val Chip = Color(0xFF262626)
}

/**
 * v1.1.1 font = the reference app's: Noto Naskh Arabic UI (Android's own Arabic system font, SIL OFL 1.1, licence in
 * assets/licenses). Matched letter by letter against ref 11 (synopsis, «مدة الحلقة», «قائمتي», «تقييمك»).
 * Its Latin and digits are stripped, so Latin text and numbers fall back to the system sans (Roboto), as in the refs.
 */
val AppFont = FontFamily(
    Font(R.font.noto_naskh_arabic_ui_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_ui_medium, FontWeight.Medium),
    Font(R.font.noto_naskh_arabic_ui_semibold, FontWeight.SemiBold),
    Font(R.font.noto_naskh_arabic_ui_bold, FontWeight.Bold),
)

/**
 * Type sizes measured on Madyan's own screenshots of the reference app (refs 05 and 11): a 360 dp wide phone with
 * a ~1.075 text scale and Android's Bold Text on, the same phone our app runs on. Synopsis and info lines 14 sp,
 * small grey lines 12 sp, top bar 20 sp. Weights are the reference app's (regular text, bold titles); the phone's
 * Bold Text setting thickens both apps the same way.
 */
object RefType {
    val TopBar = 20.sp
    val Title = 15.sp
    val Body = 14.sp
    val Small = 12.sp
    val Chip = 11.5.sp
    val Score = 18.sp
    val OutOf = 15.sp
}

private val base = Typography()
private fun TextStyle.app() = copy(fontFamily = AppFont)

val AppTypography = Typography(
    displaySmall = base.displaySmall.app(),
    headlineSmall = base.headlineSmall.app().copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleLarge = base.titleLarge.app().copy(fontWeight = FontWeight.Medium, fontSize = RefType.TopBar),
    titleMedium = base.titleMedium.app().copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
    titleSmall = base.titleSmall.app().copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = base.bodyLarge.app().copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = base.bodyMedium.app().copy(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = base.bodySmall.app().copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = base.labelLarge.app().copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = base.labelMedium.app().copy(fontSize = 12.sp),
    labelSmall = base.labelSmall.app().copy(fontSize = 11.sp),
)

private val Scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.White,
    primaryContainer = Palette.AccentDeep,
    onPrimaryContainer = Color.White,
    secondary = Palette.Accent,
    onSecondary = Color.White,
    secondaryContainer = Palette.AccentSoft,
    onSecondaryContainer = Color.White,
    background = Palette.Black,
    onBackground = Palette.Text,
    surface = Palette.Black,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerLow = Palette.Surface,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.CardHigh,
    surfaceContainerLowest = Palette.Black,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
    error = Palette.Dropped,
)

/**
 * GOLDEN RULE (v1.1): the reference app is laid out left-to-right with Arabic text — drawer and back arrow on the
 * LEFT, poster on the LEFT, actions on the RIGHT, tabs starting at the LEFT. So the whole UI is composed LTR, and
 * Arabic paragraphs that the references show right-aligned (synopsis, info grid, chips, dialog labels) say so explicitly.
 */
@Composable
fun AnimeShadowTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = Scheme, typography = AppTypography, content = content)
    }
}
