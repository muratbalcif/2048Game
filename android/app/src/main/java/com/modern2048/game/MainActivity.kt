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
    val activity = context as ComponentActivity
    val rewardedAds = remember(activity) { RewardedAds(activity) }
    var adReady by remember { mutableStateOf(false) }
    var rewardedContinue by remember { mutableStateOf(storage.loadReward()) }
    DisposableEffect(rewardedAds) {
        rewardedAds.onAvailabilityChanged = { adReady = it }
        rewardedAds.load()
        onDispose { rewardedAds.onAvailabilityChanged = null }
    }
    var winAcknowledged by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    if (game.won && !game.lost && !winAcknowledged) {
        AlertDialog(onDismissRequest = { winAcknowledged = true }, title = { Text("2048! Harika!") },
            text = { Text("2048 taşına ulaştın. Daha yüksek sayılar için devam edebilirsin.") },
            confirmButton = { TextButton(onClick = { winAcknowledged = true }) { Text("Devam Et") } },
            dismissButton = { TextButton(onClick = { game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game) }) { Text("Yeni Oyun") } })
    }
    if (game.lost) {
        AlertDialog(onDismissRequest = {}, title = { Text("Oyun Bitti") },
            text = { Text("Skorun: ${game.score}. Tekrar denemeye hazır mısın?") },
            confirmButton = {
                Column {
                    if (adReady && rewardedContinue.available) {
                        TextButton(onClick = {
                            val lostSnapshot = game
                            rewardedAds.show {
                                if (game != lostSnapshot) return@show
                                val (restored, next) = rewardedContinue.redeem(game)
                                if (next.used && !rewardedContinue.used) {
                                    game = restored
                                    rewardedContinue = next
                                    storage.saveReward(next)
                                    previous = null
                                    storage.save(game)
                                }
                            }
                        }) { Text("Reklam İzle · 3 Hamle Geri Al") }
                    }
                    TextButton(onClick = {
                        game = GameEngine.newGame()
                        previous = null
                        rewardedContinue = rewardedContinue.reset()
                        storage.saveReward(rewardedContinue)
                        winAcknowledged = false
                        storage.save(game)
                    }) { Text("Tekrar Oyna") }
                }
            },
            dismissButton = { TextButton(onClick = { previous?.let { game = it; previous = null; rewardedContinue = rewardedContinue.copy(history = rewardedContinue.history.dropLast(1)); storage.saveReward(rewardedContinue); storage.save(game) } }, enabled = previous != null) { Text("Geri Al") } })
    }
    if (confirmRestart) {
        AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text("Yeni oyun başlatılsın mı?") },
            text = { Text("Mevcut oyun ilerlemen sıfırlanacak. Rekorun korunacak.") },
            confirmButton = { TextButton(onClick = { game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game); confirmRestart = false }) { Text("Başlat") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Vazgeç") } })
    }
    Column(
        Modifier.fillMaxSize().background(canvas).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("2048", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6657E8))
        Text("Modern Puzzle", color = ink)
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    rewardedContinue = rewardedContinue.record(game)
                                    storage.saveReward(rewardedContinue)
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
            Button(onClick = { confirmRestart = true }) { Text("Yeni Oyun") }
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
