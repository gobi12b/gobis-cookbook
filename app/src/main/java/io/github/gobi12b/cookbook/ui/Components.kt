package io.github.gobi12b.cookbook.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.cookbook.data.Group
import io.github.gobi12b.cookbook.data.GroupEmoji
import io.github.gobi12b.cookbook.data.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val Emphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
/** Bouncy spring used for presses and pops. */
fun <T> bouncy() = spring<T>(dampingRatio = 0.45f, stiffness = 700f)

// ------------------------------------------------------------------ text

@Composable
fun T(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = C.Text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    display: Boolean = false,
) = Text(
    text, modifier = modifier, fontSize = size, fontWeight = weight, color = color,
    fontFamily = if (display) Fonts.Display else Fonts.Sans,
    maxLines = maxLines, overflow = TextOverflow.Ellipsis, lineHeight = lineHeight, letterSpacing = letterSpacing,
)

@Composable
fun Eyebrow(text: String, color: Color = C.TextDim, modifier: Modifier = Modifier) =
    T(text.uppercase(), size = 12.sp, weight = FontWeight.Bold, color = color, letterSpacing = 1.sp, maxLines = 1, modifier = modifier)

@Composable
fun Ic(icon: ImageVector, size: Dp = 24.dp, tint: Color = C.Text, modifier: Modifier = Modifier) =
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))

// ------------------------------------------------------------------ interaction

/** Springy squish on press (overshoots a little on release). */
fun Modifier.pressable(role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, bouncy(), label = "press")
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, role = role, onClick = onClick)
}

/**
 * The signature "sticker card": solid fill, ink outline and a hard offset shadow.
 * When clickable, pressing pushes the card down into its shadow.
 */
@Composable
fun Chunky(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    fill: Color = C.Card,
    shadow: Dp = 4.dp,
    border: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(radius),
    onClick: (() -> Unit)? = null,
    role: Role = Role.Button,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val p by animateFloatAsState(if (pressed && onClick != null) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 1200f), label = "push")
    val shadowColor = C.Shadow
    val ink = C.Ink
    val density = LocalDensity.current
    val s = with(density) { shadow.toPx() }
    val r = with(density) { radius.toPx() }
    Box(
        modifier
            .graphicsLayer { translationX = p * s; translationY = p * s }
            .drawBehind {
                val o = s * (1f - p)
                if (shape is RoundedCornerShape || shape == CircleShape) {
                    drawRoundRect(shadowColor, topLeft = Offset(o, o), size = size,
                        cornerRadius = CornerRadius(if (shape == CircleShape) size.minDimension / 2 else r))
                }
            }
            .clip(shape)
            .background(fill)
            .border(border, ink, shape)
            .then(if (onClick != null) Modifier.clickable(source, indication = null, role = role, onClick = onClick) else Modifier),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** One-shot pop-in: fade, rise, scale up from 90% and settle from a slight tilt. */
@Composable
fun Enter(delayMs: Int = 0, rise: Dp = 18.dp, tilt: Float = 0f, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(modifier.graphicsLayer {
        // Fully opaque a third of the way in, so content never lingers invisible on slow frames.
        alpha = (p.value * 3f).coerceIn(0f, 1f)
        translationY = (1f - p.value) * rise.toPx()
        val sc = 0.9f + 0.1f * p.value
        scaleX = sc; scaleY = sc
        rotationZ = (1f - p.value) * tilt
    }) { content() }
}

/** Gentle endless bob + sway, for decorative stickers. */
fun Modifier.bob(amplitude: Dp = 4.dp, degrees: Float = 6f, periodMs: Int = 2400, phase: Float = 0f): Modifier = composed {
    val t = rememberInfiniteTransition(label = "bob")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "v")
    graphicsLayer {
        val a = (v + phase) * 2 * PI.toFloat()
        translationY = sin(a) * amplitude.toPx()
        rotationZ = cos(a) * degrees
    }
}

// ------------------------------------------------------------------ images

private val bitmapCache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Thumbnail from disk, or a bright crayon tile with a big emoji. */
@Composable
fun Thumb(
    path: String?,
    colorIndex: Int?,
    modifier: Modifier = Modifier,
    emoji: String? = "🍳",
    emojiSize: TextUnit = 44.sp,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val bmp by produceState(initialValue = path?.let { bitmapCache.get(it) }, path) {
        if (path == null) { value = null; return@produceState }
        if (value == null) value = withContext(Dispatchers.IO) {
            val f = File(path)
            if (!f.exists()) null else BitmapFactory.decodeFile(path)?.asImageBitmap()?.also { bitmapCache.put(path, it) }
        }
    }
    Box(modifier.background(C.tile(colorIndex))) {
        val image = bmp
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (emoji != null) {
            Text(emoji, fontSize = emojiSize, modifier = Modifier.align(Alignment.Center).bob(3.dp, 5f))
        }
        overlay()
    }
}

@Composable
fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    val base = C.Surface
    val hi = C.SurfaceHi
    Box(modifier.drawBehind {
        val w = size.width * 2
        val start = w - x * w * 2
        drawRect(Brush.linearGradient(0f to base, .3f to base, .5f to hi, .7f to base, 1f to base,
            start = Offset(start - w, 0f), end = Offset(start, 0f)))
    })
}

// ------------------------------------------------------------------ stickers

/** Site name + emoji for a link: ("YouTube", "▶️"), ("Instagram", "📸"), … */
data class Source(val site: String, val emoji: String)

fun sourceOf(url: String): Source? {
    val u = url.lowercase()
    return when {
        "youtube.com" in u || "youtu.be" in u -> Source("YouTube", "▶️")
        "instagram.com" in u -> Source("Instagram", "📸")
        "tiktok.com" in u -> Source("TikTok", "🎵")
        "facebook.com" in u || "fb.watch" in u -> Source("Facebook", "👍")
        "vimeo.com" in u -> Source("Vimeo", "🎬")
        else -> null
    }
}

fun sourceLabel(url: String, kind: SourceKind): String {
    val s = sourceOf(url) ?: return runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull() ?: "Link"
    return when (kind) {
        SourceKind.SHORT, SourceKind.REEL, SourceKind.POST -> "${s.site} ${kind.label.lowercase()}"
        else -> s.site
    }
}

/** Little ink-outlined label stuck on an image. */
@Composable
fun SourceTag(url: String, kind: SourceKind, modifier: Modifier = Modifier) {
    val s = sourceOf(url)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(C.Card).border(1.5.dp, C.Ink, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (s != null) Text(s.emoji, fontSize = 11.sp)
        T(sourceLabel(url, kind), size = 11.sp, weight = FontWeight.Bold, maxLines = 1)
    }
}

/** Die-cut emoji sticker: white disc, ink outline, hard shadow, tilted. */
@Composable
fun Sticker(emoji: String, size: Dp = 40.dp, rotation: Float = -10f, fill: Color = C.Card, modifier: Modifier = Modifier) {
    Box(modifier.rotate(rotation)) {
        Chunky(Modifier.size(size), shape = CircleShape, fill = fill, shadow = 2.5.dp, border = 2.dp, contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

/** Sunshine starburst that says NEW!, with a wiggle. */
@Composable
fun NewBurst(modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val t = rememberInfiniteTransition(label = "burst")
    val rot by t.animateFloat(-8f, 10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "rot")
    val sun = C.Sun
    val ink = C.Ink
    val shadow = C.Shadow
    Box(modifier.size(size).graphicsLayer { rotationZ = rot }.drawBehind {
        fun star(dx: Float): Path = Path().apply {
            val cx = size.toPx() / 2 + dx; val cy = size.toPx() / 2 + dx
            val outer = size.toPx() / 2 - 2.dp.toPx(); val inner = outer * 0.74f
            for (i in 0 until 24) {
                val r = if (i % 2 == 0) outer else inner
                val a = (i * PI / 12).toFloat()
                val x = cx + r * cos(a); val y = cy + r * sin(a)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star(2.dp.toPx()), shadow)
        drawPath(star(0f), sun)
        drawPath(star(0f), ink, style = Stroke(width = 2.dp.toPx()))
    }, contentAlignment = Alignment.Center) {
        T("NEW!", size = 10.sp, weight = FontWeight.Bold, color = C.OnBright, display = true)
    }
}

/** Washi-tape label, used for "Gobi's" in the header. */
@Composable
fun Tape(text: String, color: Color = C.Sun, rotation: Float = -4f, modifier: Modifier = Modifier) {
    Box(modifier.rotate(rotation)) {
        Chunky(radius = 6.dp, fill = color, shadow = 2.dp, border = 2.dp) {
            T(text, size = 14.sp, weight = FontWeight.Bold, color = C.OnBright, display = true,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
        }
    }
}

// ------------------------------------------------------------------ chips & buttons

@Composable
fun FilterChip(label: String, selected: Boolean, emoji: String? = null, onClick: () -> Unit) {
    val fill by animateColorAsState(if (selected) C.Sun else C.Card, label = "chip")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, bouncy(), label = "chipScale")
    Box(Modifier.heightIn(min = 52.dp).padding(end = 4.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Chunky(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.height(38.dp),
            radius = 19.dp, fill = fill, shadow = if (selected) 3.dp else 2.dp, onClick = onClick, role = Role.Tab,
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (emoji != null) Text(emoji, fontSize = 15.sp)
                T(label, weight = FontWeight.Bold, color = if (selected) C.OnBright else C.Text, display = true, size = 15.sp)
            }
        }
    }
}

/** Toggle chip for choosing groups; `auto` adds a ✨ badge for picks made from the caption. */
@Composable
fun GroupToggleChip(label: String, selected: Boolean, emoji: String? = null, special: Boolean = false, auto: Boolean = false, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) { if (selected) { pop.snapTo(.8f); pop.animateTo(1f, bouncy()) } }
    Box(Modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Chunky(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.heightIn(min = 44.dp),
            radius = 14.dp, fill = if (selected) C.Sun else if (special) C.SunSoft else C.Card,
            shadow = 2.5.dp, onClick = onClick, role = Role.Checkbox, contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    special -> Ic(Icons.Plus, 16.dp, C.Text)
                    emoji != null -> Text(emoji, fontSize = 16.sp)
                }
                T(label, weight = FontWeight.Bold, color = if (selected) C.OnBright else C.Text)
                if (selected) Ic(Icons.Check, 15.dp, C.OnBright)
            }
        }
        if (auto && selected) Text("✨", fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp).bob(2.dp, 12f, 1600))
    }
}

/** Chunky push button. */
@Composable
fun Pill(label: String, icon: ImageVector? = null, primary: Boolean = false, emoji: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Chunky(
        modifier.height(50.dp), radius = 16.dp, fill = if (primary) C.Accent else C.Card, onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (emoji != null) Text(emoji, fontSize = 17.sp)
            if (icon != null) Ic(icon, 18.dp, if (primary) C.OnAccent else C.Text)
            T(label, size = 16.sp, weight = FontWeight.SemiBold, color = if (primary) C.OnAccent else C.Text, maxLines = 1, display = true)
        }
    }
}

@Composable
fun BigButton(label: String, primary: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Chunky(
        modifier.height(54.dp).alpha(if (enabled) 1f else .45f), radius = 16.dp,
        fill = if (primary) C.Accent else C.Card, onClick = if (enabled) onClick else null, contentAlignment = Alignment.Center,
    ) {
        T(label, size = 17.sp, weight = FontWeight.SemiBold, color = if (primary) C.OnAccent else C.Text, display = true)
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, size: Dp = 24.dp, tint: Color = C.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size)) }
}

/** Round chunky button (e.g. floating over a photo). */
@Composable
fun CircleBtn(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Chunky(Modifier.size(42.dp), shape = CircleShape, shadow = 3.dp, onClick = onClick, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = C.Text, modifier = Modifier.size(20.dp))
        }
    }
}

// ------------------------------------------------------------------ inputs

@Composable
fun OutlinedInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = TextStyle(color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = Fonts.Sans),
        cursorBrush = SolidColor(C.Accent),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        modifier = modifier.fillMaxWidth().height(54.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(C.Card)
                    .border(2.dp, C.Ink, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) T(placeholder, size = 16.sp, color = C.TextDim)
                inner()
            }
        },
    )
}

@Composable
fun FieldLabel(text: String) = T(text, size = 15.sp, weight = FontWeight.SemiBold, display = true)

// ------------------------------------------------------------------ snackbar

@Composable
fun CookbookSnack(text: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Chunky(modifier.fillMaxWidth().height(56.dp), radius = 18.dp, fill = C.Sun) {
        Row(Modifier.fillMaxSize().padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            T(text, size = 15.sp, weight = FontWeight.SemiBold, color = C.OnBright, maxLines = 1, display = true, modifier = Modifier.weight(1f))
            if (actionLabel != null) {
                Box(Modifier.fillMaxHeight().clickable(onClick = onAction).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    T(actionLabel, size = 15.sp, weight = FontWeight.Bold, color = C.OnBright, display = true,
                        modifier = Modifier.drawBehind {
                            drawLine(C.OnBright, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                        })
                }
            } else Spacer(Modifier.width(18.dp))
        }
    }
}

// ------------------------------------------------------------------ bottom navigation

enum class Tab { Recipes, Groups }

@Composable
fun BottomNav(current: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp).height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Chunky(Modifier.weight(1f).height(62.dp), radius = 31.dp) {
            Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NavItem("Recipes", "📖", current == Tab.Recipes) { onTab(Tab.Recipes) }
                NavItem("Groups", "🗂️", current == Tab.Groups) { onTab(Tab.Groups) }
            }
        }
        // Add button: tomato, spins its plus on press.
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val spin by animateFloatAsState(if (pressed) 90f else 0f, bouncy(), label = "spin")
        Box(Modifier.size(64.dp)) {
            Chunky(Modifier.size(62.dp), shape = CircleShape, fill = C.Accent, onClick = onAdd, contentAlignment = Alignment.Center) {
                Icon(Icons.Plus, contentDescription = "Add recipe", tint = C.OnAccent, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = spin })
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, emoji: String, selected: Boolean, onClick: () -> Unit) {
    val fill by animateColorAsState(if (selected) C.Sun else Color.Transparent, label = "nav")
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) { if (selected) { bounce.snapTo(.75f); bounce.animateTo(1f, bouncy()) } }
    Row(
        Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(26.dp)).background(fill)
            .then(if (selected) Modifier.border(2.dp, C.Ink, RoundedCornerShape(26.dp)) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value; rotationZ = (1f - bounce.value) * -40f })
        T(label, size = 16.sp, weight = FontWeight.SemiBold, color = if (selected) C.OnBright else C.TextDim, display = true)
    }
}

val NavBarHeight = 66.dp + 14.dp

// ------------------------------------------------------------------ confetti

private data class Bit(val emoji: String, val vx: Float, val vy: Float, val spin: Float, val size: Float, val delay: Float)

/** Food-emoji confetti burst; re-fires whenever [trigger] changes (ignored while 0). */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    if (trigger == 0) return
    key(trigger) {
        val bits = remember {
            val pool = listOf("🍅", "🥕", "🌶️", "🧄", "🍋", "🥑", "🍳", "🧁", "✨", "🎉", "🍩", "🥐", "🌽", "🍓")
            List(30) {
                Bit(pool.random(), Random.nextFloat() * 2f - 1f, -(0.9f + Random.nextFloat() * 0.6f),
                    Random.nextFloat() * 720f - 360f, 18f + Random.nextFloat() * 18f, Random.nextFloat() * 0.15f)
            }
        }
        val t = remember { Animatable(0f) }
        LaunchedEffect(Unit) { t.animateTo(1f, tween(1700, easing = LinearEasing)) }
        if (t.value < 1f) BoxWithConstraints(modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            bits.forEach { b ->
                val time = ((t.value - b.delay).coerceAtLeast(0f)) * 1.7f
                val x = w / 2 + b.vx * w * 0.55f * time
                val y = h * 0.62f + b.vy * h * 1.1f * time + 0.5f * h * 1.5f * time * time
                val fade = if (t.value > 0.7f) (1f - t.value) / 0.3f else 1f
                Text(
                    b.emoji, fontSize = b.size.sp,
                    modifier = Modifier.offset { IntOffset(x.toInt(), y.toInt()) }
                        .graphicsLayer { rotationZ = b.spin * time; alpha = if (time == 0f) 0f else fade },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ sheets & dialogs

@Composable
fun SheetHandle() = Box(
    Modifier.padding(top = 10.dp, bottom = 12.dp).size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(C.Ink.copy(alpha = .3f))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupPickerSheet(
    groups: List<Group>,
    initial: List<String>,
    onCreateGroup: (String, String) -> Group?,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var naming by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = C.Bg, scrimColor = C.Scrim,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            T("Pop it in a group 🗂️", size = 26.sp, weight = FontWeight.SemiBold, display = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                groups.forEach { g ->
                    GroupToggleChip(g.name, g.id in selected, emoji = g.emoji) {
                        if (g.id in selected) selected.remove(g.id) else selected.add(g.id)
                    }
                }
                GroupToggleChip("New group", false, special = true) { naming = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Cancel", false, modifier = Modifier.weight(1f), onClick = onDismiss)
                BigButton("Done", true, modifier = Modifier.weight(1f)) { onDone(selected.toList()) }
            }
        }
    }
    if (naming) NameDialog("New group", "", null, "Create", onDismiss = { naming = false }) { name, emoji ->
        onCreateGroup(name, emoji)?.let { if (it.id !in selected) selected.add(it.id) }
        naming = false
    }
}

/** Name + emoji dialog for creating or editing a group. The emoji follows the name until picked by hand. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameDialog(
    title: String,
    initial: String,
    initialEmoji: String?,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var picked by remember { mutableStateOf(initialEmoji) }
    val emoji = picked ?: GroupEmoji.guess(text.ifBlank { "dinner" })
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Bg,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sticker(emoji, 44.dp, rotation = -8f, fill = C.Sun)
                T(title, size = 24.sp, weight = FontWeight.SemiBold, display = true)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedInput(text, { text = it }, "e.g. Sunday lunch", focusRequester = focus, onIme = { if (text.isNotBlank()) onConfirm(text, emoji) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GroupEmoji.choices.forEach { e ->
                        val on = e == emoji
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (on) C.Sun else Color.Transparent)
                                .then(if (on) Modifier.border(2.dp, C.Ink, CircleShape) else Modifier)
                                .pressable { picked = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 19.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text, emoji) }, enabled = text.isNotBlank()) {
                T(confirm, size = 16.sp, weight = FontWeight.Bold, color = if (text.isNotBlank()) C.Accent else C.TextDim, display = true)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { T("Cancel", size = 16.sp, weight = FontWeight.SemiBold, color = C.TextDim, display = true) } },
    )
}

// ------------------------------------------------------------------ time

fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - then) / 60_000
    val days = daysBetween(then, now)
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "$mins min ago"
        days == 0 -> "${mins / 60} hour${if (mins / 60 == 1L) "" else "s"} ago"
        days == 1 -> "Yesterday"
        days < 7 -> "$days days ago"
        days < 14 -> "1 week ago"
        days < 31 -> "${days / 7} weeks ago"
        days < 365 -> "${days / 30} month${if (days / 30 == 1) "" else "s"} ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(then))
    }
}

/** "Updated today" / "Updated 2 days ago" / "Updated last week" — Groups list wording. */
fun updatedLabel(then: Long, now: Long = System.currentTimeMillis()): String {
    val days = daysBetween(then, now)
    return when {
        days == 0 -> "Updated today"
        days == 1 -> "Updated yesterday"
        days < 7 -> "Updated $days days ago"
        days < 14 -> "Updated last week"
        days < 31 -> "Updated ${days / 7} weeks ago"
        else -> "Updated " + SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(then))
    }
}

fun shortDate(t: Long): String {
    val sameYear = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    return SimpleDateFormat(if (sameYear) "MMM d" else "MMM d, yyyy", Locale.getDefault()).format(Date(t))
}

private fun daysBetween(a: Long, b: Long): Int {
    val zone = ZoneId.systemDefault()
    fun day(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(day(a), day(b)).toInt()
}
