package com.modern2048.game

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
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

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
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
    var showLeaderboard by remember { mutableStateOf(false) }
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
    val leaderboard = remember(context) { Leaderboard(context) }
    var ranking by remember { mutableStateOf(LeaderboardResult(emptyList(), null)) }
    var rankingLoading by remember { mutableStateOf(false) }
    var verifiedSession by remember { mutableStateOf(storage.loadVerified()?.takeIf { session ->
        VerifiedEngine.replay(session.seed, session.moves)?.let { it.board == game.board && it.score == game.score } == true
    }) }
    LaunchedEffect(verifiedSession) { storage.saveVerified(verifiedSession) }
    var startingVerified by remember { mutableStateOf(false) }
    LaunchedEffect(game.lost, game.score, gameMode) {
        if (game.lost) {
            rankingLoading = true
            verifiedSession?.let { session ->
                if (VerifiedEngine.replay(session.seed, session.moves)?.score == game.score) {
                    if (leaderboard.finalizeVerified(session, game.score)) {
                        verifiedSession = null
                        storage.saveVerified(null)
                    }
                }
            }
            ranking = leaderboard.load(gameMode)
            rankingLoading = false
        }
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
                                val result = verifiedSession?.let { session ->
                                     val next = VerifiedEngine.replay(session.seed, session.moves + VerifiedEngine.letter(direction))
                                     if (next != null) MoveResult(next, next.board != game.board, next.score - game.score)
                                     else MoveResult(game, false, 0)
                                 } ?: GameEngine.move(game, direction)
                                if (result.moved) {
                                    if (verifiedSession != null) {
                                        verifiedSession = verifiedSession?.copy(moves = verifiedSession!!.moves + VerifiedEngine.letter(direction))
                                        storage.saveVerified(verifiedSession)
                                    }
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
    fun startRanked() {
        if (startingVerified || motionActive) return
        startingVerified = true
        scope.launch {
            val session = leaderboard.startVerified(gameMode)
            startingVerified = false
            if (session != null) {
                resetMotion()
                verifiedSession = session
                game = VerifiedEngine.initial(session.seed)
                storage.saveVerified(session)
                previous = null
                rewardedContinue = rewardedContinue.reset()
                storage.saveReward(rewardedContinue)
                winAcknowledged = false
                storage.save(game)
            }
        }
    }
    LaunchedEffect(Unit) {
        if (verifiedSession == null && !game.lost) startRanked()
    }
    if (!motionActive && gameMode.reached(game.board) && !game.lost && !winAcknowledged) {
        AlertDialog(onDismissRequest = { winAcknowledged = true }, title = { Text("Hedefe Ulaştın!") },
            text = { Text("${gameMode.target} taşına ulaştın. Daha yüksek sayılar için devam edebilirsin.") },
            confirmButton = { TextButton(onClick = { winAcknowledged = true }) { Text("Devam Et") } },
            dismissButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); verifiedSession = null; storage.saveVerified(null); game = GameEngine.newGame(); startRanked(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game) }) { Text("Yeni Oyun") } })
    }
    if (!motionActive && game.lost) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = selectedTheme.canvas,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(68.dp).background(selectedTheme.board, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("★", fontSize = 38.sp, color = selectedTheme.accent)
                    }
                    Text("Oyun Bitti!", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                    Text("Harika bir denemeydi! Bir sonraki oyunda rekorunu geçebilirsin.",
                        fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameOverStat("SKOR", game.score.toString(), selectedTheme, Modifier.weight(1f))
                        GameOverStat("REKOR", best.toString(), selectedTheme, Modifier.weight(1f))
                    }
                    Column(
                        Modifier.fillMaxWidth().background(selectedTheme.board.copy(alpha = .65f), RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("🏆  Global Liderlik", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = selectedTheme.ink, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch { rankingLoading = true; ranking = leaderboard.load(gameMode); rankingLoading = false }
                            }) { Text("Yenile", fontSize = 11.sp, color = selectedTheme.accent) }
                        }
                        if (rankingLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ranking.error?.let { Text(it, fontSize = 12.sp, color = selectedTheme.ink.copy(alpha = .7f)) }
                        ranking.entries.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.rank}. ${entry.name}" + if (entry.mine) " (Sen)" else "",
                                    fontSize = 12.sp, fontWeight = if (entry.mine) FontWeight.Bold else FontWeight.Normal,
                                    color = selectedTheme.ink, modifier = Modifier.weight(1f))
                                Text(entry.score.toString(), fontSize = 12.sp, color = selectedTheme.ink)
                            }
                        }
                        ranking.myRank?.let { Text("Senin sıran: #$it", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = selectedTheme.accent) }
                    }
                    Button(
                        onClick = {
                            resetMotion()
                            progress = progress.nextGame()
                            storage.saveProgress(progress)
                            verifiedSession = null
                            storage.saveVerified(null)
                            game = GameEngine.newGame()
                            startRanked()
                            previous = null
                            rewardedContinue = rewardedContinue.reset()
                            storage.saveReward(rewardedContinue)
                            winAcknowledged = false
                            storage.save(game)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent)
                    ) { Text("↻  Yeniden Oyna", fontWeight = FontWeight.Bold) }
                    if (verifiedSession == null && adReady && rewardedContinue.available) {
                        OutlinedButton(
                            onClick = {
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
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("▶  Reklam İzle · 3 Hamle Geri Al", fontSize = 12.sp, color = selectedTheme.ink) }
                    }
                    if (previous != null && verifiedSession == null) {
                        TextButton(onClick = {
                            previous?.let {
                                game = it
                                previous = null
                                rewardedContinue = rewardedContinue.copy(history = rewardedContinue.history.dropLast(1))
                                storage.saveReward(rewardedContinue)
                                storage.save(game)
                            }
                        }) { Text("← Son Hamleyi Geri Al", color = selectedTheme.accent) }
                    }
                }
            }
        }
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
                                verifiedSession = null
                                storage.saveVerified(null)
                                game = GameEngine.newGame()
                                startRanked()
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
        ModalBottomSheet(onDismissRequest = { showProgress = false },
            containerColor = selectedTheme.canvas) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("İstatistikler", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                Text("Her hamle gelişiminin bir parçası", fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreCard("TOPLAM PUAN", "%,d".format(progress.totalPoints), Modifier.weight(1f), selectedTheme)
                    ScoreCard("EN BÜYÜK TAŞ", progress.bestTile.toString(), Modifier.weight(1f), selectedTheme)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreCard("OYUN", progress.gamesStarted.toString(), Modifier.weight(1f), selectedTheme)
                    ScoreCard("HAMLE", progress.moves.toString(), Modifier.weight(1f), selectedTheme)
                }
                Surface(color = selectedTheme.board.copy(alpha = .7f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Oyun performansı", fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                        Text("Toplam birleşme: ${progress.merges}", color = selectedTheme.ink)
                        Text("Oyun başına ortalama puan: ${progress.totalPoints / progress.gamesStarted.coerceAtLeast(1)}", color = selectedTheme.ink)
                        Text("Hamle başına birleşme: %${if (progress.moves == 0) 0 else progress.merges * 100 / progress.moves}", color = selectedTheme.ink)
                    }
                }
                Text("Başarımlar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                GameAchievements.list(progress).forEach { achievement ->
                    Surface(color = if (achievement.unlocked) selectedTheme.accent.copy(alpha = .10f) else selectedTheme.board.copy(alpha = .5f),
                        shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (achievement.unlocked) "✦" else "○", color = selectedTheme.accent, fontSize = 21.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(achievement.title, fontWeight = FontWeight.SemiBold, color = selectedTheme.ink)
                        }
                    }
                }
            }
        }
    }
    if (showLeaderboard) {
        LaunchedEffect(showLeaderboard, gameMode) {
            rankingLoading = true
            ranking = leaderboard.load(gameMode)
            rankingLoading = false
        }
        ModalBottomSheet(onDismissRequest = { showLeaderboard = false }, containerColor = selectedTheme.canvas) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("🏆 Global sıralama", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                Text("Dünyanın dört bir yanından oyuncular", fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(gameMode.title, color = selectedTheme.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { rankingLoading = true; ranking = leaderboard.load(gameMode); rankingLoading = false } }) { Text("↻ Yenile") }
                }
                if (rankingLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ranking.myRank?.let { rank ->
                    Surface(color = selectedTheme.accent.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
                        Text("Senin sıralaman  #$rank", Modifier.fillMaxWidth().padding(18.dp),
                            color = selectedTheme.accent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                }
                ranking.error?.let { Text(it, color = selectedTheme.ink.copy(alpha = .7f)) }
                if (!rankingLoading && ranking.entries.isEmpty() && ranking.error == null)
                    Text("Henüz doğrulanmış skor yok. İlk sırayı sen al!", color = selectedTheme.ink)
                ranking.entries.forEach { entry ->
                    Surface(color = if (entry.mine) selectedTheme.accent.copy(alpha = .12f) else selectedTheme.board.copy(alpha = .65f),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (entry.rank == 1) "🥇" else if (entry.rank == 2) "🥈" else if (entry.rank == 3) "🥉" else "#${entry.rank}",
                                modifier = Modifier.width(44.dp), fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                            Text(entry.name + if (entry.mine) " (Sen)" else "", modifier = Modifier.weight(1f),
                                fontWeight = if (entry.mine) FontWeight.Bold else FontWeight.Medium, color = selectedTheme.ink)
                            Text("%,d".format(entry.score), fontWeight = FontWeight.ExtraBold, color = selectedTheme.accent)
                        }
                    }
                }
            }
        }
    }
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            containerColor = selectedTheme.canvas,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Oyun ayarları", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                        Text("Oyun deneyimini kendine göre düzenle", fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                    }
                    TextButton(onClick = { showSettings = false }) { Text("Bitti", color = selectedTheme.accent) }
                }
                Text("GÖRÜNÜM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    GameTheme.entries.forEach { option ->
                        val active = option == selectedTheme
                        Column(
                            modifier = Modifier.weight(1f)
                                .background(option.board, RoundedCornerShape(18.dp))
                                .clickable { selectedTheme = option; storage.saveTheme(option) }
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(2, 8, 32).forEach { number ->
                                    Box(
                                        Modifier.size(22.dp).background(option.tile(number), RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { Text(number.toString(), fontSize = 10.sp, color = Color(0xFF24243A), fontWeight = FontWeight.Bold) }
                                }
                            }
                            Text(option.title, fontSize = 12.sp, color = option.ink, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            Text(if (active) "● Seçili" else "○ Seç", fontSize = 11.sp, color = if (active) option.accent else option.ink.copy(alpha = .65f))
                        }
                    }
                }
                Text("ERİŞİLEBİLİRLİK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                Surface(shape = RoundedCornerShape(20.dp), color = selectedTheme.board.copy(alpha = .75f)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SettingsToggle("Yüksek kontrast", "Taşları daha belirgin göster", highContrast, selectedTheme.ink) { highContrast = it; storage.saveHighContrast(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle("Büyük taş sayıları", "Taş değerlerini daha kolay oku", largeNumbers, selectedTheme.ink) { largeNumbers = it; storage.saveLargeNumbers(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle("Yön tuşları", "Kaydırmadan da hamle yap", directionalControls, selectedTheme.ink) { directionalControls = it; storage.saveDirectionalControls(it) }
                    }
                }
                Text("OYUN DENEYİMİ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                Surface(shape = RoundedCornerShape(20.dp), color = selectedTheme.board.copy(alpha = .75f)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SettingsToggle("Ses efektleri", "Birleşme ve başarı sesleri", soundEnabled, selectedTheme.ink) { soundEnabled = it; storage.saveSoundEnabled(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle("Titreşim", "Hamlelerde dokunsal geri bildirim", hapticsEnabled, selectedTheme.ink) { hapticsEnabled = it; storage.saveHapticsEnabled(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle("Animasyonları azalt", "Daha sakin ve hızlı geçişler", reducedMotion, selectedTheme.ink) { reducedMotion = it; storage.saveReducedMotion(it) }
                    }
                }
            }
        }
    }
    if (confirmRestart) {
        AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text("Yeni oyun başlatılsın mı?") },
            text = { Text("Mevcut oyun ilerlemen sıfırlanacak. Rekorun korunacak.") },
            confirmButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game); confirmRestart = false }) { Text("Başlat") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Vazgeç") } })
    }
    Column(
        Modifier.fillMaxSize().background(selectedTheme.canvas)
            .verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("2048", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.accent)
        TextButton(onClick = { showModePicker = true }, enabled = !motionActive) { Text(gameMode.title + " ▾", color = selectedTheme.ink) }
        TextButton(onClick = { showLeaderboard = true }) { Text("🏆 Sıralama", color = selectedTheme.accent) }

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
                enabled = previous != null && !motionActive && verifiedSession == null
            ) { Text("Geri Al", fontSize = 12.sp) }
            Button(onClick = { confirmRestart = true }) { Text("Yeni Oyun", fontSize = 12.sp) }
            OutlinedButton(onClick = { showSettings = true }, enabled = !motionActive) { Text("Ayarlar", fontSize = 12.sp) }
        }
        TextButton(onClick = { showProgress = true }, enabled = !motionActive) { Text("İstatistikler ve Başarımlar") }
        Spacer(Modifier.height(8.dp))
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

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    textColor: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = textColor.copy(alpha = .65f), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GameOverStat(label: String, value: String, theme: GameTheme, modifier: Modifier = Modifier) {
    Column(
        modifier.background(theme.board.copy(alpha = .8f), RoundedCornerShape(16.dp)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.ink.copy(alpha = .65f))
        Text(value, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = theme.ink)
    }
}
