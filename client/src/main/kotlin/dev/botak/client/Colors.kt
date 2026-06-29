package dev.botak.client

import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color

/**
 * Dark Material color palette for the BotakTTS desktop UI.
 *
 * The background and surface colors are semi-transparent so the always-on-top input window can
 * overlay other applications without fully occluding them.
 */
val darkColors =
    darkColors(
        primary = Color(0xFF90CAF9),
        primaryVariant = Color(0xFF1976D2),
        secondary = Color(0xFFCE93D8),
        // Semi-transparent black
        background = Color(0x80000000),
        // Semi-transparent dark gray
        surface = Color(0xB0121212),
        onBackground = Color.White,
        onSurface = Color.White,
    )
