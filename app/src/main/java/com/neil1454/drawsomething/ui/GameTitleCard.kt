package com.neil1454.drawsomething.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neil1454.drawsomething.core.GameKind
import com.neil1454.drawsomething.core.Sound
import com.neil1454.drawsomething.sound.Sfx
import kotlinx.coroutines.delay

/** Every game has its own little signature tune, so each one has a character of its own when it starts. */
fun GameKind.titleSound(): Sound = when (this) {
    GameKind.DRAW, GameKind.MEMORY, GameKind.HOLIDAY, GameKind.KALAH, GameKind.TRIVIA -> Sound.JINGLE_B
    GameKind.MINES, GameKind.CENTIPEDE, GameKind.HANGMAN -> Sound.JINGLE_G
    GameKind.SEA, GameKind.INVADERS -> Sound.JINGLE_E
    GameKind.DROP, GameKind.DICE, GameKind.LUDO -> Sound.JINGLE_D
    GameKind.FLIP, GameKind.LIARSDICE, GameKind.BLACKJACK -> Sound.JINGLE_C
    GameKind.QUICK, GameKind.GUESSWHO, GameKind.NOUGHTS -> Sound.JINGLE_H
    GameKind.HILO, GameKind.DRAUGHTS, GameKind.MONOPOLY, GameKind.SHOWDOWN -> Sound.JINGLE_F
    GameKind.TETRIS, GameKind.ARKANOID, GameKind.HOCKEY -> Sound.JINGLE_A
    GameKind.DEALNODEAL -> Sound.SUSPENSE
    GameKind.SLOTS -> Sound.CASH
}

/**
 * The title card that flashes up when a game begins: its picture, its name and a line about it on the game's own colours, with its own jingle.
 * It never blocks the game underneath, and goes by itself after a second and a half.
 */
@Composable
fun GameTitleCard(kind: GameKind, playing: Boolean) {
    var visible by remember { mutableStateOf(false) }
    val scale = remember { Animatable(0.4f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(kind, playing) {
        if (!playing) { visible = false; return@LaunchedEffect }
        visible = true
        scale.snapTo(0.4f); fade.snapTo(0f)
        Sfx.play(kind.titleSound())
        fade.animateTo(1f, tween(220))
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        delay(1100)
        fade.animateTo(0f, tween(380))
        visible = false
    }
    if (!visible) return
    val cols = kind.colors()
    Box(Modifier.fillMaxSize().alpha(fade.value).background(Brush.verticalGradient(listOf(cols.first().copy(alpha = 0.92f), cols.last().copy(alpha = 0.92f), Color(0xF2050814)))), contentAlignment = Alignment.Center) {
        Column(Modifier.scale(scale.value).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GameIcon(kind, size = 120.dp)
            androidx.compose.material3.Text(kind.label.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
            androidx.compose.material3.Text(kind.blurb, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
