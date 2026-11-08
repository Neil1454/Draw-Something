package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Scheme = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    secondary = Color(0xFFFF8A3D),
    tertiary = Color(0xFF1DB98A),
    background = Color(0xFFF7F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFECE9FF),
    error = Color(0xFFD32F2F),
)

@Composable
fun DrawSomethingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

/** A centred column with a sensible max width, so it looks right on phones and tablets. */
@Composable
fun Page(scroll: Boolean = true, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
        ) { content() }
    }
}
