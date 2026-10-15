package com.modern2048.game

enum class GameMode(val title: String, val target: Int?) {
    CLASSIC("Klasik 2048", 2048),
    TARGET_1024("Hedef 1024", 1024),
    ENDLESS("Süresiz", null);

    fun reached(board: List<Int>): Boolean = target != null && board.any { it >= target }
}
