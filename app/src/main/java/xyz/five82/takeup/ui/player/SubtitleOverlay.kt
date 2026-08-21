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
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import xyz.five82.takeup.ui.theme.TakeupSubtitle

// Cues drawn in Compose rather than through Media3's SubtitleView, which cannot
// round a corner or pad a background box. We only ever play SRT: text cues, no
// bitmaps, no embedded fonts or sizes, so there is little of that view left to
// miss. Emphasis and {\anX} placement, the two things SRT does carry, are
// handled below.

// Fraction of the video surface height, matching Media3's own sizing so cues
// stay put across screen sizes. Converted through toSp so the rendered size is
// exactly this many pixels whatever the system font scale.
private const val TEXT_SIZE_FRACTION = 0.055f

// Distance from the edge the video sits at, again as a fraction of height.
// Media3 uses the same 0.08 for cues that do not place themselves.
private const val EDGE_FRACTION = 0.08f

private val BOX_COLOR = Color.Black.copy(alpha = 0.70f)

@Composable
fun SubtitleOverlay(player: Player, lift: Dp, modifier: Modifier = Modifier) {
    var cues by remember { mutableStateOf(player.currentCues.cues) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }
        }
        player.addListener(listener)
        cues = player.currentCues.cues
        onDispose { player.removeListener(listener) }
    }

    if (cues.isEmpty()) return

    BoxWithConstraints(modifier) {
        val fontSize = with(LocalDensity.current) {
            (TEXT_SIZE_FRACTION * constraints.maxHeight).toSp()
        }
        val edge = maxHeight * EDGE_FRACTION
        for (cue in cues) {
            val text = cue.text ?: continue
            val alignment = cue.alignment()
            val atTop = alignment == Alignment.TopStart ||
                alignment == Alignment.TopCenter ||
                alignment == Alignment.TopEnd
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
                    .align(alignment)
                    // The console rises over the bottom of the frame, so only
                    // cues sitting down there need to move.
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (atTop) edge else 0.dp,
                        bottom = if (atTop) 0.dp else edge + lift,
                    )
                    .widthIn(max = maxWidth * 0.9f)
                    .background(BOX_COLOR, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// SRT carries placement only as {\anX}, which Media3 turns into a line and a
// position. Reading them as thirds covers all nine without pretending to the
// precision of a format that cannot express any more than that.
private fun Cue.alignment(): Alignment {
    val vertical = when {
        line == Cue.DIMEN_UNSET -> 1
        line < 0.35f -> -1
        line > 0.65f -> 1
        else -> 0
    }
    val horizontal = when {
        position == Cue.DIMEN_UNSET -> 0
        position < 0.35f -> -1
        position > 0.65f -> 1
        else -> 0
    }
    return when (vertical) {
        -1 -> when (horizontal) {
            -1 -> Alignment.TopStart
            1 -> Alignment.TopEnd
            else -> Alignment.TopCenter
        }
        0 -> when (horizontal) {
            -1 -> Alignment.CenterStart
            1 -> Alignment.CenterEnd
            else -> Alignment.Center
        }
        else -> when (horizontal) {
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
