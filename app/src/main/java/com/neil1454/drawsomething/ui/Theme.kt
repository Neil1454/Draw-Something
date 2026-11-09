package com.neil1454.drawsomething.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The palette: deep indigo night with neon pops. */
object Funk {
    val Ink = Color(0xFF140B33)
    val Night = Color(0xFF1E1250)
    val Grape = Color(0xFF2B1470)
    val Pink = Color(0xFFFF3D9A)
    val Cyan = Color(0xFF1EE6FF)
    val Lime = Color(0xFFB6FF3B)
    val Sun = Color(0xFFFFD23F)
    val Orange = Color(0xFFFF7A2F)
    val Violet = Color(0xFF8B5CF6)
    val Mint = Color(0xFF2DE2A6)
    val Red = Color(0xFFFF4D4D)
    val Blue = Color(0xFF3D8BFF)
}

private val Scheme = darkColorScheme(
    primary = Funk.Pink,
    onPrimary = Color.White,
    secondary = Funk.Orange,
    onSecondary = Color.White,
    tertiary = Funk.Mint,
    onTertiary = Color(0xFF05261C),
    background = Funk.Ink,
    onBackground = Color.White,
    surface = Color(0xFF2A1B6B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF3A2A85),
    onSurfaceVariant = Color(0xFFD9D0FF),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF7A1F3A),
    onErrorContainer = Color.White,
    primaryContainer = Color(0xFF5A1D5F),
    onPrimaryContainer = Color.White,
    secondaryContainer = Color(0xFF4A2A9A),
    onSecondaryContainer = Color.White,
    outline = Color(0xFF8E7FD6),
)

private val FunkTypography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Black),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Black),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun DrawSomethingTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = FunkTypography) {
        FunkyBackground(Modifier.fillMaxSize()) {
            Surface(
                Modifier.fillMaxSize().safeDrawingPadding(),
                color = Color.Transparent,
                contentColor = Color.White,
            ) { content() }
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
