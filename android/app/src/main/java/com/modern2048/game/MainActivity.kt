package com.modern2048.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val canvas = Color(0xFFFAF8F5)
private val ink = Color(0xFF24243A)
private val boardColor = Color(0xFFE9E5E2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GameScreen() } }
    }
}

@Composable
private fun GameScreen() {
    val context = LocalContext.current
    val storage = remember(context) { GameStorage(context) }
    var game by remember { mutableStateOf(storage.load() ?: GameEngine.newGame().also(storage::save)) }
    var previous by remember { mutableStateOf<GameState?>(null) }
    var best by remember { mutableIntStateOf(storage.best()) }
    Column(
        Modifier.fillMaxSize().background(canvas).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("2048", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6657E8))
        Text("Modern Puzzle", color = ink)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard("SKOR", game.score.toString(), Modifier.weight(1f))
            ScoreCard("REKOR", best.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth()
                .pointerInput(game) {
                    var drag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            if (abs(drag.x) > 30f || abs(drag.y) > 30f) {
                                val direction = if (abs(drag.x) > abs(drag.y)) {
                                    if (drag.x > 0) Direction.RIGHT else Direction.LEFT
                                } else {
                                    if (drag.y > 0) Direction.DOWN else Direction.UP
                                }
                                val result = GameEngine.move(game, direction)
                                if (result.moved) {
                                    previous = game
                                    game = result.state
                                    best = maxOf(best, game.score)
                                    storage.save(game)
                                    storage.saveBest(best)
                                }
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    )
                }
                .background(boardColor, RoundedCornerShape(24.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { col ->
                        val value = game.board[row * 4 + col]
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .background(tileColor(value), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (value != 0) Text(
                                value.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (value >= 1024) 19.sp else 25.sp,
                                color = ink
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Kaydırarak aynı sayıları birleştir", color = ink)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { previous?.let { game = it; previous = null; storage.save(game) } },
                enabled = previous != null
            ) { Text("Geri Al") }
            Button(onClick = { game = GameEngine.newGame(); previous = null; storage.save(game) }) { Text("Yeni Oyun") }
        }
    }
}

@Composable
private fun ScoreCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

private fun tileColor(value: Int): Color = when (value) {
    0 -> Color(0xFFF5F1ED)
    2 -> Color(0xFFFFF0DA)
    4 -> Color(0xFFFFE0BA)
    8 -> Color(0xFFFFB16E)
    16 -> Color(0xFFFF866F)
    32 -> Color(0xFFFF6D93)
    64 -> Color(0xFFD879EC)
    128 -> Color(0xFF67B8F4)
    256 -> Color(0xFF43C5C2)
    512 -> Color(0xFF6FCF97)
    1024 -> Color(0xFFB9A0FF)
    else -> Color(0xFFFFD15C)
}
