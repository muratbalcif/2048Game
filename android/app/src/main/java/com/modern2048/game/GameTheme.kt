package com.modern2048.game

import androidx.compose.ui.graphics.Color

enum class GameTheme(val title: String, val canvas: Color, val ink: Color, val board: Color, val accent: Color) {
    SUNSET("Gün Batımı", Color(0xFFFAF8F5), Color(0xFF24243A), Color(0xFFE9E5E2), Color(0xFF6657E8)),
    OCEAN("Okyanus", Color(0xFFEAF7F9), Color(0xFF15354B), Color(0xFFBEDDE5), Color(0xFF087E98)),
    MIDNIGHT("Gece", Color(0xFF16192A), Color(0xFFF5F4FA), Color(0xFF30354D), Color(0xFFA69BFF));

    fun tile(value: Int): Color {
        if (value == 0) return if (this == MIDNIGHT) Color(0xFF41465F) else Color(0xFFF5F1ED)
        val palette: List<Color> = when (this) {
            SUNSET -> listOf(Color(0xFFFFF0DA), Color(0xFFFFE0BA), Color(0xFFFFB16E), Color(0xFFFF866F), Color(0xFFFF6D93), Color(0xFFD879EC), Color(0xFF67B8F4), Color(0xFF43C5C2), Color(0xFF6FCF97), Color(0xFFB9A0FF), Color(0xFFFFD15C))
            OCEAN -> listOf(Color(0xFFD5F5F2), Color(0xFFAAE9E6), Color(0xFF7EDAD9), Color(0xFF54C6CF), Color(0xFF4AA8CE), Color(0xFF608FD4), Color(0xFF8583DC), Color(0xFF9F80D2), Color(0xFF6AC8AC), Color(0xFF40ADAA), Color(0xFFFFCE81))
            MIDNIGHT -> listOf(Color(0xFFB4ADD8), Color(0xFFA79FDD), Color(0xFF8B8AD6), Color(0xFF747BC8), Color(0xFF696EB7), Color(0xFF8669B8), Color(0xFFA166BA), Color(0xFFBA6BA8), Color(0xFFCF818F), Color(0xFFDB9C76), Color(0xFFF2C875))
        }
        val index = (31 - Integer.numberOfLeadingZeros(value)).minus(1).coerceIn(0, palette.lastIndex)
        return palette[index]
    }
}
