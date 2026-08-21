package xyz.five82.takeup.ui.player

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import xyz.five82.takeup.ui.theme.TakeupSubtitle

// Cues drawn in Compose rather than through Media3's SubtitleView, which cannot
// round a corner or pad a background box. We only ever play SRT: text cues, no
// bitmaps, no embedded fonts or sizes, so there is little of that view left to
// miss. Emphasis and {\anX} placement, the two things SRT does carry, are
// handled below.

// Fraction of the displayed picture height, matching Media3's own sizing so
// cues stay put across screen sizes and video aspect ratios. Converted through
// toSp so the rendered size is exactly this many pixels whatever the system
// font scale.
private const val TEXT_SIZE_FRACTION = 0.055f

// Distance from the edge the video sits at, again as a fraction of height.
// Media3 uses the same 0.08 for cues that do not place themselves.
private const val EDGE_FRACTION = 0.08f

private val BOX_COLOR = Color.Black.copy(alpha = 0.70f)

/** The centered rectangle where a fit-mode PlayerView draws the picture. */
internal fun subtitleVideoRect(surface: Size, aspect: Float?): Rect {
    if (surface.width <= 0f || surface.height <= 0f ||
        aspect == null || !aspect.isFinite() || aspect <= 0f
    ) {
        return Rect(Offset.Zero, surface)
    }
    val surfaceAspect = surface.width / surface.height
    val width: Float
    val height: Float
    if (aspect > surfaceAspect) {
        width = surface.width
        height = width / aspect
    } else {
        height = surface.height
        width = height * aspect
    }
    return Rect(
        left = (surface.width - width) / 2f,
        top = (surface.height - height) / 2f,
        right = (surface.width + width) / 2f,
        bottom = (surface.height + height) / 2f,
    )
}

@Composable
fun SubtitleOverlay(player: Player, lift: Dp, cropped: Boolean, modifier: Modifier = Modifier) {
    var cues by remember { mutableStateOf(player.currentCues.cues) }
    var videoAspect by remember { mutableStateOf(player.videoSize.displayAspect()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoAspect = videoSize.displayAspect()
            }
        }
        player.addListener(listener)
        cues = player.currentCues.cues
        videoAspect = player.videoSize.displayAspect()
        onDispose { player.removeListener(listener) }
    }

    if (cues.isEmpty()) return

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val video = subtitleVideoRect(
            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            if (cropped) null else videoAspect,
        )
        val fontSize = with(density) { (TEXT_SIZE_FRACTION * video.height).toSp() }
        val videoWidth = with(density) { video.width.toDp() }
        val videoHeight = with(density) { video.height.toDp() }
        val side = with(density) { video.left.toDp() }
        val above = with(density) { video.top.toDp() }
        val below = with(density) { (constraints.maxHeight - video.bottom).toDp() }
        val edge = videoHeight * EDGE_FRACTION
        for (cue in cues) {
            val text = cue.text ?: continue
            val vertical = cue.verticalAnchor()
            Text(
                text = text.toAnnotatedString(),
                color = Color.White,
                fontFamily = TakeupSubtitle,
                fontWeight = FontWeight.Medium,
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                textAlign = TextAlign.Center,
                // The shadow keeps letters legible where a bright shot shows
                // through the box.
                style = TextStyle(
                    shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f),
                ),
                modifier = Modifier
                    .align(cue.alignment(vertical))
                    // Keep authored positions relative to the picture. Only
                    // bottom cues rise to clear the player console.
                    .padding(
                        start = side + 24.dp,
                        end = side + 24.dp,
                        top = when (vertical) {
                            VerticalAnchor.Top -> above + edge
                            VerticalAnchor.Middle -> above
                            VerticalAnchor.Bottom -> 0.dp
                        },
                        bottom = when (vertical) {
                            VerticalAnchor.Top -> 0.dp
                            VerticalAnchor.Middle -> below
                            VerticalAnchor.Bottom -> maxOf(below + edge, lift)
                        },
                    )
                    .widthIn(max = videoWidth * 0.9f)
                    .background(BOX_COLOR, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

private fun VideoSize.displayAspect(): Float? {
    if (width <= 0 || height <= 0 ||
        !pixelWidthHeightRatio.isFinite() || pixelWidthHeightRatio <= 0f
    ) {
        return null
    }
    return (width.toFloat() * pixelWidthHeightRatio / height).takeIf {
        it.isFinite() && it > 0f
    }
}

private enum class VerticalAnchor { Top, Middle, Bottom }

// SRT carries placement only as {\anX}, which Media3 turns into a line and a
// position. Reading them as thirds covers all nine without pretending to the
// precision of a format that cannot express any more than that.
private fun Cue.verticalAnchor() = when {
    line == Cue.DIMEN_UNSET -> VerticalAnchor.Bottom
    line < 0.35f -> VerticalAnchor.Top
    line > 0.65f -> VerticalAnchor.Bottom
    else -> VerticalAnchor.Middle
}

private fun Cue.alignment(vertical: VerticalAnchor): Alignment {
    val horizontal = when {
        position == Cue.DIMEN_UNSET -> 0
        position < 0.35f -> -1
        position > 0.65f -> 1
        else -> 0
    }
    return when (vertical) {
        VerticalAnchor.Top -> when (horizontal) {
            -1 -> Alignment.TopStart
            1 -> Alignment.TopEnd
            else -> Alignment.TopCenter
        }
        VerticalAnchor.Middle -> when (horizontal) {
            -1 -> Alignment.CenterStart
            1 -> Alignment.CenterEnd
            else -> Alignment.Center
        }
        VerticalAnchor.Bottom -> when (horizontal) {
            -1 -> Alignment.BottomStart
            1 -> Alignment.BottomEnd
            else -> Alignment.BottomCenter
        }
    }
}

// SRT emphasis only. A cue's own colours are dropped on purpose: the point of
// drawing these ourselves is one deliberate look.
private fun CharSequence.toAnnotatedString(): AnnotatedString {
    val spanned = this as? Spanned ?: return AnnotatedString(toString())
    return buildAnnotatedString {
        append(spanned.toString())
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    Typeface.BOLD_ITALIC -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start,
                        end,
                    )
                }
                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
        }
    }
}
