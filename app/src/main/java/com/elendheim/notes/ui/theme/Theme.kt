package com.elendheim.notes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The palette: near-black backgrounds, a soft purple accent for interactive
// elements, and the vivid logo purple reserved for the primary action.
val Ink = Color(0xFF0E0E12)
val Surface1 = Color(0xFF15151A)
val Surface2 = Color(0xFF1C1C23)
val Surface3 = Color(0xFF23232C)
val OutlineDim = Color(0xFF2C2C36)
val SoftPurple = Color(0xFFB79CED)
val SoftPurpleDim = Color(0xFF8E77BF)
val LogoPurple = Color(0xFF9900FF)
val TextPrimary = Color(0xFFECECF1)
val TextSecondary = Color(0xFF9C9CA8)
val OnAccent = Color(0xFF1A0F2E)
val Danger = Color(0xFFE58B8B)

// Muted tag colors for note stripes. Stored as hex strings on the note.
val NoteTagColors = listOf(
    "#B79CED" to "Purple",
    "#8E77BF" to "Violet",
    "#7FA6D9" to "Blue",
    "#85B79D" to "Green",
    "#CBAF87" to "Sand",
    "#C98B9E" to "Rose",
    "#8F949E" to "Grey"
)

fun parseTagColor(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

private val DarkColors = darkColorScheme(
    primary = SoftPurple,
    onPrimary = OnAccent,
    primaryContainer = Surface3,
    onPrimaryContainer = SoftPurple,
    secondary = SoftPurpleDim,
    onSecondary = OnAccent,
    tertiary = LogoPurple,
    background = Ink,
    onBackground = TextPrimary,
    surface = Ink,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    outline = OutlineDim,
    outlineVariant = OutlineDim,
    error = Danger
)

private val NotesTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp)
    )
}

private val NotesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = NotesTypography,
        shapes = NotesShapes,
        content = content
    )
}
