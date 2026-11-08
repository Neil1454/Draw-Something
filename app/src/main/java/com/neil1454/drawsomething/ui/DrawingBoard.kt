package com.neil1454.drawsomething.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.neil1454.drawsomething.core.StrokeBoard
import com.neil1454.drawsomething.core.StrokeData

/**
 * A square canvas that fills the space it is given (centred). Everything is stored in 0..1
 * coordinates, so the drawer's phone and the guesser's tablet show the same picture.
 *
 * [version] changes whenever the board changes, which is what triggers a redraw.
 */
@Composable
fun SquareBoard(
    board: StrokeBoard,
    version: Int,
    modifier: Modifier = Modifier,
    canDraw: Boolean = false,
    color: Int = 0,
    width: Float = 0.013f,
    onBegin: (Float, Float) -> Unit = { _, _ -> },
    onMove: (Float, Float) -> Unit = { _, _ -> },
    onEnd: () -> Unit = {},
) {
    // The gesture loop outlives recompositions, so always call the latest callbacks
    // (they capture the currently selected colour and brush size).
    val begin by rememberUpdatedState(onBegin)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val shape = RoundedCornerShape(12.dp)
        Canvas(
            Modifier
                .size(side)
                .clip(shape)
                .background(Color.White)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), shape)
                .pointerInput(canDraw) {
                    if (!canDraw) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        begin(
                            (down.position.x / size.width).coerceIn(0f, 1f),
                            (down.position.y / size.height).coerceIn(0f, 1f),
                        )
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.positionChanged()) {
                                change.consume()
                                move(
                                    (change.position.x / size.width).coerceIn(0f, 1f),
                                    (change.position.y / size.height).coerceIn(0f, 1f),
                                )
                            }
                        } while (event.changes.any { it.pressed })
                        end()
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val redrawOn = version
            board.strokes.forEach { drawStrokeData(it) }
        }
    }
}

private fun DrawScope.drawStrokeData(s: StrokeData) {
    val w = size.width
    val p = s.pts
    if (p.size < 2) return
    val colour = Color(s.color)
    val px = s.width * w
    if (p.size < 4) {
        drawCircle(colour, radius = px / 2f, center = Offset(p[0] * w, p[1] * w))
        return
    }
    val path = Path().apply {
        moveTo(p[0] * w, p[1] * w)
        var i = 2
        while (i + 1 < p.size) {
            lineTo(p[i] * w, p[i + 1] * w)
            i += 2
        }
    }
    drawPath(path, colour, style = Stroke(width = px, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
