package io.github.gobi12b.cookbook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gobi12b.cookbook.R

/**
 * "Sticker book" palette: buttery paper, chocolate ink outlines, hard offset shadows,
 * and a box of bright crayons (tomato, sunshine, mint, bubblegum, lilac, sky).
 */
class Palette(
    val bg: Color,
    val card: Color,
    val surface: Color,
    val surfaceHi: Color,
    val ink: Color,        // text + outlines
    val textDim: Color,
    val shadow: Color,     // hard offset shadow
    val accent: Color,     // tomato
    val onAccent: Color,
    val sun: Color,        // mustard highlight (selected states)
    val sunSoft: Color,
    val mint: Color,
    val scrim: Color,
    val dark: Boolean,
)

private val Crayons = listOf(0xFFFFB38A, 0xFFFFD45C, 0xFF8FE0B8, 0xFFFF9FCB, 0xFFC4B5FF, 0xFF9AD3FF).map(::Color)

val LightPalette = Palette(
    bg = Color(0xFFFFF6E6), card = Color(0xFFFFFFFF), surface = Color(0xFFFCEACB), surfaceHi = Color(0xFFFFF5E3),
    ink = Color(0xFF2A1A10), textDim = Color(0xFF7B6352), shadow = Color(0xFF2A1A10),
    accent = Color(0xFFFF5A36), onAccent = Color(0xFFFFFFFF), sun = Color(0xFFFFC83D), sunSoft = Color(0xFFFFEDB0),
    mint = Color(0xFF2E9E6A), scrim = Color(0x662A1A10), dark = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF1D1510), card = Color(0xFF2B2019), surface = Color(0xFF3A2C22), surfaceHi = Color(0xFF4A392C),
    ink = Color(0xFFFCEBD2), textDim = Color(0xFFBBA58F), shadow = Color(0xFF000000),
    accent = Color(0xFFFF6B47), onAccent = Color(0xFFFFFFFF), sun = Color(0xFFFFC83D), sunSoft = Color(0xFF4A3A16),
    mint = Color(0xFF6FD6A3), scrim = Color(0x99000000), dark = true,
)

/** Colour tokens. The activity is recreated on a light/dark switch, so plain property reads are safe. */
object C {
    internal var p: Palette = LightPalette
    val Bg get() = p.bg
    val Card get() = p.card
    val Surface get() = p.surface
    val SurfaceHi get() = p.surfaceHi
    val Text get() = p.ink
    val Ink get() = p.ink
    val TextDim get() = p.textDim
    val Shadow get() = p.shadow
    val Accent get() = p.accent
    val OnAccent get() = p.onAccent
    val Sun get() = p.sun
    val SunSoft get() = p.sunSoft
    val Mint get() = p.mint
    val Scrim get() = p.scrim
    val Dark get() = p.dark
    /** Text that sits on a bright crayon or sun fill — always dark, in both themes. */
    val OnBright = Color(0xFF2A1A10)
    fun tile(i: Int?) = if (i == null) p.surface else Crayons[i.mod(Crayons.size)]
}

/** Fredoka (round & chunky) for headlines; Plus Jakarta Sans for reading. */
@OptIn(ExperimentalTextApi::class)
object Fonts {
    private fun round(w: Int) = Font(R.font.fredoka, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    private fun sans(w: Int) = Font(R.font.jakarta, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

    val Display = FontFamily(round(400), round(500), round(600), round(700))
    val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700), sans(800))
}

@Composable
fun CookbookTheme(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette
    C.p = palette
    val base = if (palette.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = palette.accent, onPrimary = palette.onAccent, background = palette.bg, onBackground = palette.ink,
        surface = palette.bg, onSurface = palette.ink, surfaceContainer = palette.card, surfaceContainerHigh = palette.card,
        surfaceContainerHighest = palette.surface, surfaceContainerLow = palette.card, onSurfaceVariant = palette.textDim,
        outline = palette.ink, secondaryContainer = palette.surface,
    )
    MaterialTheme(colorScheme = scheme) {
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontFamily = Fonts.Sans), content)
    }
}

/** 24×24 chunky line icons. */
object Icons {
    private fun icon(name: String, block: ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.fill(d: String) = addPath(addPathNodes(d), fill = SolidColor(Color.White))

    private fun ImageVector.Builder.stroke(d: String, width: Float = 2.3f) =
        addPath(
            addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )

    val Search = icon("search") { stroke("M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z"); stroke("M20 20l-4-4") }
    val Plus = icon("plus") { stroke("M12 5v14M5 12h14", 3f) }
    val More = icon("more") {
        fill("M6.8 12a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z")
        fill("M13.8 12a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z")
        fill("M20.8 12a1.8 1.8 0 1 1-3.6 0a1.8 1.8 0 1 1 3.6 0z")
    }
    val Play = icon("play") { fill("M8 5.5v13l11-6.5z") }
    val Back = icon("back") { stroke("M15 5l-7 7 7 7", 2.8f) }
    val Share = icon("share") { stroke("M4 12v7h16v-7M12 4v12M8 8l4-4 4 4", 2.4f) }
    val Check = icon("check") { stroke("M5 12l5 5L20 7", 3f) }
    val Close = icon("close") { stroke("M6 6l12 12M18 6L6 18", 2.6f) }
    val Link = icon("link") {
        stroke("M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1")
        stroke("M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1")
    }
    val Shuffle = icon("sort") { stroke("M4 7h16M7 12h10M10 17h4", 2.4f) }
}
