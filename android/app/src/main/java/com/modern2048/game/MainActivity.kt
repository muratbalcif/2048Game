package com.modern2048.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val effects = rememberGameEffects(context)
    DisposableEffect(effects) { onDispose { effects.release() } }
    var game by remember { mutableStateOf(storage.load() ?: GameEngine.newGame().also(storage::save)) }
    var motion by remember { mutableStateOf<List<TileMotion>>(emptyList()) }
    var motionActive by remember { mutableStateOf(false) }
    var revealedCells by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var mergedCells by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val motionProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var slideJob by remember { mutableStateOf<Job?>(null) }
    var popJob by remember { mutableStateOf<Job?>(null) }
    fun resetMotion() {
        slideJob?.cancel()
        popJob?.cancel()
        slideJob = null
        popJob = null
        motionActive = false
        motion = emptyList()
        revealedCells = emptySet()
        mergedCells = emptySet()
    }
    var previous by remember { mutableStateOf<GameState?>(null) }
    var progress by remember { mutableStateOf(storage.loadProgress()) }
    var gameMode by remember { mutableStateOf(storage.loadMode()) }
    var showModePicker by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
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
    LaunchedEffect(gameMode, game.board) {
        if (!gameMode.reached(game.board)) winAcknowledged = false
    }
    var confirmRestart by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(storage.loadTheme()) }
    var highContrast by remember { mutableStateOf(storage.highContrast()) }
    var largeNumbers by remember { mutableStateOf(storage.largeNumbers()) }
    var directionalControls by remember { mutableStateOf(storage.directionalControls()) }
    var soundEnabled by remember { mutableStateOf(storage.soundEnabled()) }
    var reducedMotion by remember { mutableStateOf(storage.reducedMotion()) }
    var hapticsEnabled by remember { mutableStateOf(storage.hapticsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, storage) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!motionActive) storage.save(game)
                storage.saveReward(rewardedContinue)
                storage.saveProgress(progress)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val performMove: (Direction) -> Unit = { direction ->
        if (!motionActive && !game.lost) {
                                val result = GameEngine.move(game, direction)
                                if (result.moved) {
                                    rewardedContinue = rewardedContinue.record(game)
                                    storage.saveReward(rewardedContinue)
                                    previous = game
                                    val mergedCount = TileMotionPlanner.plan(game.board, direction).count { it.merged } / 2
                                    progress = progress.recordMove(game, result.state, result.scoreDelta, mergedCount)
                                    storage.saveProgress(progress)
                                    effects.play(result.scoreDelta, !game.won && result.state.won, soundEnabled, hapticsEnabled)
                                    if (!reducedMotion) {
                                        motion = TileMotionPlanner.plan(game.board, direction)
                                        motionActive = true
                                        slideJob = scope.launch {
                                            motionProgress.snapTo(0f)
                                            motionProgress.animateTo(1f, tween(160))
                                            val newCells = result.state.board.indices.filter { result.state.board[it] != 0 && motion.none { tile -> tile.to == it } }.toSet()
                                            revealedCells = newCells
                                            mergedCells = motion.filter { it.merged }.map { it.to }.toSet()
                                            game = result.state
                                            motionActive = false
                                            motion = emptyList()
                                            motionProgress.snapTo(0f)
                                            popJob?.cancel()
                                            popJob = scope.launch {
                                                kotlinx.coroutines.delay(180)
                                                revealedCells = emptySet()
                                                mergedCells = emptySet()
                                            }
                                        }
                                    } else game = result.state
                                    best = maxOf(best, result.state.score)
                                    storage.save(result.state)
                                    storage.saveBest(best)
                                }
        }
    }
    val currentMove by rememberUpdatedState(performMove)
    if (!motionActive && gameMode.reached(game.board) && !game.lost && !winAcknowledged) {
        AlertDialog(onDismissRequest = { winAcknowledged = true }, title = { Text("Hedefe Ulaştın!") },
            text = { Text("${gameMode.target} taşına ulaştın. Daha yüksek sayılar için devam edebilirsin.") },
            confirmButton = { TextButton(onClick = { winAcknowledged = true }) { Text("Devam Et") } },
            dismissButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game) }) { Text("Yeni Oyun") } })
    }
    if (!motionActive && game.lost) {
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
                        resetMotion()
                        progress = progress.nextGame()
                        storage.saveProgress(progress)
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
    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text("Oyun Modu Seç") },
            text = {
                Column {
                    Text("Mod değiştirildiğinde yeni oyun başlar.")
                    GameMode.entries.forEach { candidate ->
                        TextButton(onClick = {
                            if (candidate != gameMode) {
                                resetMotion()
                                gameMode = candidate
                                storage.saveMode(candidate)
                                progress = progress.nextGame()
                                storage.saveProgress(progress)
                                game = GameEngine.newGame()
                                storage.save(game)
                                previous = null
                                rewardedContinue = rewardedContinue.reset()
                                storage.saveReward(rewardedContinue)
                                winAcknowledged = false
                            }
                            showModePicker = false
                        }) { Text(candidate.title) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModePicker = false }) { Text("Vazgeç") } }
        )
    }
    if (showProgress) {
        AlertDialog(onDismissRequest = { showProgress = false }, title = { Text("İstatistikler ve Başarımlar") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hamle: ${progress.moves}  ·  Birleşme: ${progress.merges}")
                Text("Oyun: ${progress.gamesStarted}  ·  En büyük taş: ${progress.bestTile}")
                Text("Toplam puan: ${progress.totalPoints}")
                HorizontalDivider()
                GameAchievements.list(progress).forEach { achievement ->
                    Text("${if (achievement.unlocked) "✓" else "○"}  ${achievement.title}",
                        color = if (achievement.unlocked) ink else Color.Gray)
                }
            } }, confirmButton = { TextButton(onClick = { showProgress = false }) { Text("Kapat") } })
    }
    if (showSettings) {
        AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Oyun Ayarları") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Tema", fontWeight = FontWeight.Bold)
                GameTheme.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedTheme == option, onClick = { selectedTheme = option; storage.saveTheme(option) })
                        Text(option.title)
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yüksek kontrast", Modifier.weight(1f))
                    Switch(highContrast, onCheckedChange = { highContrast = it; storage.saveHighContrast(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Büyük taş sayıları", Modifier.weight(1f))
                    Switch(largeNumbers, onCheckedChange = { largeNumbers = it; storage.saveLargeNumbers(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yön tuşları", Modifier.weight(1f))
                    Switch(directionalControls, onCheckedChange = { directionalControls = it; storage.saveDirectionalControls(it) })
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ses efektleri", modifier = Modifier.weight(1f))
                    Switch(checked = soundEnabled, onCheckedChange = { soundEnabled = it; storage.saveSoundEnabled(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Titreşim", modifier = Modifier.weight(1f))
                    Switch(checked = hapticsEnabled, onCheckedChange = { hapticsEnabled = it; storage.saveHapticsEnabled(it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Animasyonları azalt", modifier = Modifier.weight(1f))
                    Switch(checked = reducedMotion, onCheckedChange = { reducedMotion = it; storage.saveReducedMotion(it) })
                }
            } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Kapat") } })
    }
    if (confirmRestart) {
        AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text("Yeni oyun başlatılsın mı?") },
            text = { Text("Mevcut oyun ilerlemen sıfırlanacak. Rekorun korunacak.") },
            confirmButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game); confirmRestart = false }) { Text("Başlat") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Vazgeç") } })
    }
    Column(
        Modifier.fillMaxSize().background(selectedTheme.canvas)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("2048", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.accent)
        TextButton(onClick = { showModePicker = true }, enabled = !motionActive) { Text(gameMode.title + " ▾", color = selectedTheme.ink) }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard("SKOR", game.score.toString(), Modifier.weight(1f), selectedTheme)
            ScoreCard("REKOR", best.toString(), Modifier.weight(1f), selectedTheme)
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .pointerInput(Unit) {
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
                                currentMove(direction)
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    )
                }
                .background(selectedTheme.board, RoundedCornerShape(24.dp)).padding(10.dp)
        ) {
            val cell = (maxWidth - 30.dp) / 4
            val step = cell + 10.dp
            repeat(16) { index ->
                val value = if (motionActive) 0 else game.board[index]
                AnimatedTile(
                    value,
                    Modifier.size(cell).offset(x = step * (index % 4), y = step * (index / 4)),
                    reducedMotion,
                    theme = selectedTheme,
                    highContrast = highContrast,
                    largeNumbers = largeNumbers,
                    pop = index in revealedCells || index in mergedCells
                )
            }
            if (motionActive) {
                motion.forEach { tile ->
                    val dx = (tile.to % 4 - tile.from % 4) * motionProgress.value
                    val dy = (tile.to / 4 - tile.from / 4) * motionProgress.value
                    AnimatedTile(
                        tile.value,
                        Modifier.size(cell).offset(
                            x = step * (tile.from % 4 + dx),
                            y = step * (tile.from / 4 + dy)
                        ),
                        true,
                        theme = selectedTheme,
                        highContrast = highContrast,
                        largeNumbers = largeNumbers
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (directionalControls) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(onClick = { performMove(Direction.UP) }, enabled = !motionActive) { Text("↑ Yukarı") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { performMove(Direction.LEFT) }, enabled = !motionActive) { Text("← Sol") }
                    OutlinedButton(onClick = { performMove(Direction.DOWN) }, enabled = !motionActive) { Text("↓ Aşağı") }
                    OutlinedButton(onClick = { performMove(Direction.RIGHT) }, enabled = !motionActive) { Text("Sağ →") }
                }
            }
        }
        Text("Kaydırarak aynı sayıları birleştir", color = selectedTheme.ink)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { previous?.let { game = it; previous = null; rewardedContinue = rewardedContinue.copy(history = rewardedContinue.history.dropLast(1)); storage.saveReward(rewardedContinue); storage.save(game) } },
                enabled = previous != null && !motionActive
            ) { Text("Geri Al", fontSize = 12.sp) }
            Button(onClick = { confirmRestart = true }, enabled = !motionActive) { Text("Yeni Oyun", fontSize = 12.sp) }
            OutlinedButton(onClick = { showSettings = true }, enabled = !motionActive) { Text("Ayarlar", fontSize = 12.sp) }
        }
        TextButton(onClick = { showProgress = true }, enabled = !motionActive) { Text("İstatistikler ve Başarımlar") }
    }
}

@Composable
private fun ScoreCard(label: String, value: String, modifier: Modifier = Modifier, theme: GameTheme = GameTheme.SUNSET) {
    Column(
        modifier = modifier.background(if (theme == GameTheme.MIDNIGHT) theme.board else Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = theme.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun AnimatedTile(value: Int, modifier: Modifier = Modifier, reducedMotion: Boolean = false, theme: GameTheme = GameTheme.SUNSET, highContrast: Boolean = false, largeNumbers: Boolean = false, pop: Boolean = false) {
    val color by animateColorAsState(if (highContrast) { if (value == 0) Color.White else if (value >= 128) Color(0xFFFFC400) else Color(0xFF20B6DF) } else theme.tile(value), animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else spring(), label = "tile-color")
    val scale by animateFloatAsState(
        targetValue = if (value == 0) 0.96f else 1f,
        animationSpec = if (reducedMotion) androidx.compose.animation.core.snap() else spring(),
        label = "tile-scale"
    )
    val popScale by animateFloatAsState(
        targetValue = if (pop && !reducedMotion) 1.12f else 1f,
        animationSpec = spring(), label = "tile-pop"
    )
    Box(
        modifier = modifier.scale(scale * popScale).background(color, RoundedCornerShape(14.dp))
            .semantics { contentDescription = if (value == 0) "Boş hücre" else "$value taşı" },
        contentAlignment = Alignment.Center
    ) {
        if (value != 0) Text(
            value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = if (value >= 1024) (if (largeNumbers) 22.sp else 19.sp) else (if (largeNumbers) 30.sp else 25.sp),
            color = ink
        )
    }
}
