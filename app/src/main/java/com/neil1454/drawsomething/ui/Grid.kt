package com.neil1454.drawsomething.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A square grid that fills the space it is given (centred). [cell] draws one square and is told
 * the square's approximate size in dp so text and emoji can be scaled to fit.
 */
@Composable
fun SquareGrid(
    w: Int,
    h: Int,
    modifier: Modifier = Modifier,
    cell: @Composable BoxScope.(x: Int, y: Int, cellDp: Float) -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val cellDp = side.value / maxOf(w, h)
        Column(Modifier.size(side)) {
            for (y in 0 until h) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (x in 0 until w) {
                        Box(Modifier.weight(1f).fillMaxHeight()) { cell(x, y, cellDp) }
                    }
                }
            }
        }
    }
}
