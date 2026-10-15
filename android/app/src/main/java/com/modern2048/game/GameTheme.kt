package com.modern2048.game

import androidx.compose.ui.graphics.Color

enum class GameTheme(val title: String, val canvas: Color, val ink: Color, val board: Color, val accent: Color) {
    SUNSET("Gün Batımı", Color(0xFFFAF8F5), Color(0xFF24243A), Color(0xFFE9E5E2), Color(0xFF6657E8)),
    OCEAN("Okyanus", Color(0xFFEAF7F9), Color(0xFF15354B), Color(0xFFBEDDE5), Color(0xFF087E98)),
    MIDNIGHT("Gece", Color(0xFF16192A), Color(0xFFF5F4FA), Color(0xFF30354D), Color(0xFFA69BFF));

    fun tile(value: Int): Color {
        if (value == 0) return if (this == MIDNIGHT) Color(0xFF41465F) else Color(0xFFF5F1ED)
        val palette = when (this) {
            SUNSET -> listOf(0xFFFFF0DA, 0xFFFFE0BA, 0xFFFFB16E, 0xFFFF866F, 0xFFFF6D93, 0xFFD879EC, 0xFF67B8F4, 0xFF43C5C2, 0xFF6FCF97, 0xFFB9A0FF, 0xFFFFD15C)
            OCEAN -> listOf(0xFFD5F5F2, 0xFFAAE9E6, 0xFF7EDAD9, 0xFF54C6CF, 0xFF4AA8CE, 0xFF608FD4, 0xFF8583DC, 0xFF9F80D2, 0xFF6AC8AC, 0xFF40ADAA, 0xFFFFCE81)
            MIDNIGHT -> listOf(0xFFB4ADD8, 0xFFA79FDD, 0xFF8B8AD6, 0xFF747BC8, 0xFF696EB7, 0xFF8669B8, 0xFFA166BA, 0xFFBA6BA8, 0xFFCF818F, 0xFFDB9C76, 0xFFF2C875)
        }
        val index = (31 - Integer.numberOfLeadingZeros(value)).minus(1).coerceIn(0, palette.lastIndex)
        return Color(palette[index])
    }
}
