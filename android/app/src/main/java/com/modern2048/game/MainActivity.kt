package com.modern2048.game

import android.annotation.SuppressLint
import android.os.Bundle
import com.google.android.gms.ads.MobileAds
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.LocaleListCompat
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
import androidx.compose.ui.res.stringResource
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

class MainActivity : AppCompatActivity() {
    private lateinit var interstitialAds: InterstitialAds

    fun onNewGameStarted() = interstitialAds.onNewGame()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedLanguage = getSharedPreferences("language_settings", MODE_PRIVATE).getString("language", "") ?: ""
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
        MobileAds.initialize(this)
        interstitialAds = InterstitialAds(this)
        setContent { MaterialTheme { GameScreen() } }
        window.decorView.post { interstitialAds.onHomeReady() }
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
    var previous by remember { mutableStateOf(storage.loadReward().history.lastOrNull()) }
    var progress by remember { mutableStateOf(storage.loadProgress()) }
    var gameMode by remember { mutableStateOf(storage.loadMode()) }
    var showModePicker by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf("") }
    var nicknameSaved by remember { mutableStateOf(false) }
    var nicknameError by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(storage.best()) }
    val activity = context as MainActivity
    val rewardedAds = remember(activity) { RewardedAds(activity) }
    var adReady by remember { mutableStateOf(false) }
    var doubledGameKey by remember { mutableStateOf(storage.doubledGameKey()) }
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
    LaunchedEffect(Unit) { nicknameDraft = leaderboard.nickname }
    var ranking by remember { mutableStateOf(LeaderboardResult(emptyList(), null)) }
    var rankingLoading by remember { mutableStateOf(false) }
    var verifiedSession by remember { mutableStateOf(storage.loadVerified()?.takeIf { session ->
        VerifiedEngine.replay(session.seed, session.moves)?.let { it.board == game.board && it.score == game.score } == true
    }) }
    var rewardedScoreSession by remember { mutableStateOf<VerifiedGame?>(null) }
    var rewardChecking by remember { mutableStateOf(false) }
    var rewardMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(verifiedSession) { storage.saveVerified(verifiedSession) }
    var startingVerified by remember { mutableStateOf(false) }
    LaunchedEffect(game.lost, game.score, gameMode) {
        if (game.lost) {
            rankingLoading = true
            verifiedSession?.let { session ->
                if (VerifiedEngine.replay(session.seed, session.moves)?.score == game.score) {
                    if (leaderboard.finalizeVerified(session, game.score)) {
                        rewardedScoreSession = session
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
        AlertDialog(onDismissRequest = { winAcknowledged = true }, title = { Text(stringResource(R.string.win_title)) },
            text = { Text(stringResource(R.string.target_reached_desc, gameMode.target ?: 2048)) },
            confirmButton = { TextButton(onClick = { winAcknowledged = true }) { Text(stringResource(R.string.devam)) } },
            dismissButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); activity.onNewGameStarted(); verifiedSession = null; rewardedScoreSession = null; storage.saveVerified(null); game = GameEngine.newGame(); startRanked(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game) }) { Text(stringResource(R.string.new_game)) } })
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
                    Text(stringResource(R.string.game_over), fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                    Text(stringResource(R.string.game_over_subtitle),
                        fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    val gameKey = "${game.score}:${game.board.joinToString(",")}"
                    val doubled = doubledGameKey == gameKey
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GameOverStat(stringResource(R.string.score), (if (doubled) game.score.toLong() * 2 else game.score.toLong()).toString(), selectedTheme, Modifier.weight(1f))
                        GameOverStat(stringResource(R.string.best), best.toString(), selectedTheme, Modifier.weight(1f))
                    }
                    Column(
                        Modifier.fillMaxWidth().background(selectedTheme.board.copy(alpha = .65f), RoundedCornerShape(18.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.leaderboard_gameover), fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = selectedTheme.ink, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                scope.launch { rankingLoading = true; ranking = leaderboard.load(gameMode); rankingLoading = false }
                            }) { Text(stringResource(R.string.refresh), fontSize = 11.sp, color = selectedTheme.accent) }
                        }
                        if (rankingLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ranking.error?.let { Text(it, fontSize = 12.sp, color = selectedTheme.ink.copy(alpha = .7f)) }
                        ranking.entries.forEach { entry ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.rank}. ${entry.name}" + if (entry.mine) stringResource(R.string.you_suffix) else "",
                                    fontSize = 12.sp, fontWeight = if (entry.mine) FontWeight.Bold else FontWeight.Normal,
                                    color = selectedTheme.ink, modifier = Modifier.weight(1f))
                                Text(entry.score.toString(), fontSize = 12.sp, color = selectedTheme.ink)
                            }
                        }
                        ranking.myRank?.let { Text(stringResource(R.string.your_rank, it), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = selectedTheme.accent) }
                    }
                    Button(
                        onClick = {
                            resetMotion()
                            progress = progress.nextGame()
                            storage.saveProgress(progress)
                            activity.onNewGameStarted()
                            verifiedSession = null
                            rewardedScoreSession = null
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
                    ) { Text(stringResource(R.string.replay), fontWeight = FontWeight.Bold) }
                    if (doubled) {
                        Text(stringResource(R.string.reward_success),
                            fontSize = 12.sp, color = selectedTheme.accent)
                    } else if (game.score > 0) {
                        val rewardSession = rewardedScoreSession ?: verifiedSession
                        Button(
                            enabled = adReady && !rewardChecking && rewardSession != null,
                            onClick = {
                                val session = rewardSession ?: return@Button
                                val snapshot = game
                                val claimKey = gameKey
                                rewardedAds.show(onRewardEarned = {
                                    if (game == snapshot && game.lost && doubledGameKey != claimKey) {
                                        rewardChecking = true
                                        rewardMessage = context.getString(R.string.reward_wait)
                                        scope.launch {
                                            var verified = false
                                            repeat(12) {
                                                if (!verified) {
                                                    verified = leaderboard.isRewardVerified(session)
                                                    if (!verified) kotlinx.coroutines.delay(2500)
                                                }
                                            }
                                            if (verified && game == snapshot && game.lost && doubledGameKey != claimKey) {
                                                doubledGameKey = claimKey
                                                storage.saveDoubledGameKey(claimKey)
                                                val bonusScore = (game.score.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                                best = maxOf(best, bonusScore)
                                                storage.saveBest(best)
                                                ranking = leaderboard.load(gameMode)
                                                rewardMessage = null
                                            } else {
                                                rewardMessage = context.getString(R.string.reward_retry)
                                            }
                                            rewardChecking = false
                                        }
                                    }
                                }, sessionId = session.sessionId, playerId = session.playerId)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent)
                        ) {
                            Text("🎁  ", fontSize = 18.sp)
                            Text(if (rewardChecking) stringResource(R.string.reward_checking) else stringResource(R.string.watch_double),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        rewardMessage?.let { Text(it, fontSize = 12.sp, color = selectedTheme.ink) }
                        if (!rewardChecking && rewardMessage != null && rewardSession != null) {
                            TextButton(onClick = {
                                rewardChecking = true
                                scope.launch {
                                    val verified = leaderboard.isRewardVerified(rewardSession)
                                    if (verified && game.lost && doubledGameKey != gameKey) {
                                        doubledGameKey = gameKey
                                        storage.saveDoubledGameKey(gameKey)
                                        best = maxOf(best, (game.score.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                        storage.saveBest(best)
                                        ranking = leaderboard.load(gameMode)
                                        rewardMessage = null
                                    } else rewardMessage = context.getString(R.string.reward_pending)
                                    rewardChecking = false
                                }
                            }) { Text(stringResource(R.string.check_reward)) }
                        }
                    }
                    if (!doubled && verifiedSession == null && adReady && rewardedContinue.available) {
                        Button(
                            onClick = {
                                val lostSnapshot = game
                                rewardedAds.show( {
                                    if (game != lostSnapshot) return@show
                                    val (restored, next) = rewardedContinue.redeem(game)
                                    if (restored != game && next.history.isEmpty()) {
                                        game = restored
                                        rewardedContinue = next
                                        storage.saveReward(next)
                                        previous = null
                                        storage.save(game)
                                    }
                                })
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = selectedTheme.accent)
                        ) {
                            Text("↩  ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(stringResource(R.string.watch_undo), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    if (!doubled && previous != null && verifiedSession == null) {
                        TextButton(onClick = {
                            previous?.let {
                                game = it
                                previous = null
                                rewardedContinue = rewardedContinue.copy(history = rewardedContinue.history.dropLast(1))
                                storage.saveReward(rewardedContinue)
                                storage.save(game)
                            }
                        }) { Text(stringResource(R.string.no_undo), color = selectedTheme.accent) }
                    }
                }
            }
        }
    }
    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text(stringResource(R.string.mode_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.mode_change))
                    GameMode.entries.forEach { candidate ->
                        TextButton(onClick = {
                            if (candidate != gameMode) {
                                resetMotion()
                                gameMode = candidate
                                storage.saveMode(candidate)
                                progress = progress.nextGame()
                                storage.saveProgress(progress)
                                activity.onNewGameStarted()
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
                        }) { Text(stringResource(when (candidate) { GameMode.CLASSIC -> R.string.classic; GameMode.TARGET_1024 -> R.string.target_1024; GameMode.ENDLESS -> R.string.endless })) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (showProgress) {
        ModalBottomSheet(onDismissRequest = { showProgress = false },
            containerColor = selectedTheme.canvas) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.statistics), fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                Text(stringResource(R.string.stats_subtitle), fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreCard(stringResource(R.string.total_score), "%,d".format(progress.totalPoints), Modifier.weight(1f), selectedTheme)
                    ScoreCard(stringResource(R.string.biggest_tile), progress.bestTile.toString(), Modifier.weight(1f), selectedTheme)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreCard(stringResource(R.string.games), progress.gamesStarted.toString(), Modifier.weight(1f), selectedTheme)
                    ScoreCard(stringResource(R.string.moves), progress.moves.toString(), Modifier.weight(1f), selectedTheme)
                }
                Surface(color = selectedTheme.board.copy(alpha = .7f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.performance), fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                        Text(stringResource(R.string.total_merges, progress.merges), color = selectedTheme.ink)
                        Text(stringResource(R.string.avg_score, progress.totalPoints / progress.gamesStarted.coerceAtLeast(1)), color = selectedTheme.ink)
                        Text(stringResource(R.string.merge_rate, if (progress.moves == 0) 0 else progress.merges * 100 / progress.moves), color = selectedTheme.ink)
                    }
                }
                Text(stringResource(R.string.achievements), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                GameAchievements.list(progress).forEach { achievement ->
                    Surface(color = if (achievement.unlocked) selectedTheme.accent.copy(alpha = .10f) else selectedTheme.board.copy(alpha = .5f),
                        shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (achievement.unlocked) "✦" else "○", color = selectedTheme.accent, fontSize = 21.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(when (achievement.id) { "first_merge" -> R.string.ach_first_merge; "hundred_moves" -> R.string.ach_hundred_moves; "tile_128" -> R.string.ach_tile_128; "tile_512" -> R.string.ach_tile_512; "tile_2048" -> R.string.ach_tile_2048; else -> R.string.ach_five_games }), fontWeight = FontWeight.SemiBold, color = selectedTheme.ink)
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
                Text(stringResource(R.string.global_leaderboard), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                Text(stringResource(R.string.leaderboard_subtitle), fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                androidx.compose.material3.OutlinedTextField(value = nicknameDraft, onValueChange = { if (it.length <= 20) { nicknameDraft = it; nicknameSaved = false; nicknameError = false } }, label = { Text("Oyuncu rumuzun") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val validNickname = nicknameDraft.trim().matches(Regex("[\\p{L}\\p{N} _.-]{2,20}"))
                Button(onClick = { scope.launch { nicknameSaved = leaderboard.updateNickname(nicknameDraft); nicknameError = !nicknameSaved; if (nicknameSaved) { nicknameDraft = leaderboard.nickname; ranking = leaderboard.load(gameMode) } } }, enabled = validNickname && nicknameDraft.trim() != leaderboard.nickname) { Text("Rumuzu kaydet") }
                 if (nicknameSaved) Text("Rumuz ve mevcut skorun global sıralamada güncellendi.", fontSize = 12.sp, color = selectedTheme.accent)
                 if (nicknameError) Text("Rumuz sunucuda güncellenemedi. Backend yayını ve sıralamalı oyun oturumunu kontrol et.", fontSize = 12.sp, color = Color.Red)
                Text("Rumuz değiştiğinde mevcut skorun ve oyuncu kimliğin korunur.", fontSize = 12.sp, color = selectedTheme.ink.copy(alpha = .65f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(when (gameMode) { GameMode.CLASSIC -> R.string.classic; GameMode.TARGET_1024 -> R.string.target_1024; GameMode.ENDLESS -> R.string.endless }), color = selectedTheme.accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { rankingLoading = true; ranking = leaderboard.load(gameMode); rankingLoading = false } }) { Text(stringResource(R.string.refresh_icon)) }
                }
                if (rankingLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ranking.myRank?.let { rank ->
                    Surface(color = selectedTheme.accent.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
                        Text(stringResource(R.string.your_ranking, rank), Modifier.fillMaxWidth().padding(18.dp),
                            color = selectedTheme.accent, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                }
                ranking.error?.let { Text(it, color = selectedTheme.ink.copy(alpha = .7f)) }
                if (!rankingLoading && ranking.entries.isEmpty() && ranking.error == null)
                    Text(stringResource(R.string.empty_leaderboard), color = selectedTheme.ink)
                ranking.entries.forEach { entry ->
                    Surface(color = if (entry.mine) selectedTheme.accent.copy(alpha = .12f) else selectedTheme.board.copy(alpha = .65f),
                        shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (entry.rank == 1) "🥇" else if (entry.rank == 2) "🥈" else if (entry.rank == 3) "🥉" else "#${entry.rank}",
                                modifier = Modifier.width(44.dp), fontWeight = FontWeight.Bold, color = selectedTheme.ink)
                            Text(entry.name + if (entry.mine) stringResource(R.string.you_suffix) else "", modifier = Modifier.weight(1f),
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
                        Text(stringResource(R.string.settings_title), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.ink)
                        Text(stringResource(R.string.settings_subtitle), fontSize = 13.sp, color = selectedTheme.ink.copy(alpha = .65f))
                    }
                    TextButton(onClick = { showSettings = false }) { Text(stringResource(R.string.done), color = selectedTheme.accent) }
                }
                var languageExpanded by remember { mutableStateOf(false) }
                val languagePrefs = remember(context) { context.getSharedPreferences("language_settings", android.content.Context.MODE_PRIVATE) }
                var languageChoice by remember { mutableStateOf(languagePrefs.getString("language", "") ?: "") }
                Column {
                    Text(stringResource(R.string.language), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                    Box {
                        OutlinedButton(onClick = { languageExpanded = true }) {
                            Text(when (languageChoice) {
                                "tr" -> stringResource(R.string.language_turkish)
                                "en" -> stringResource(R.string.language_english)
                                else -> stringResource(R.string.language_system)
                            })
                        }
                        DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                            listOf("" to R.string.language_system, "tr" to R.string.language_turkish, "en" to R.string.language_english).forEach { (code, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    languageExpanded = false
                                    languageChoice = code
                                    languagePrefs.edit().putString("language", code).apply()
                                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                })
                            }
                        }
                    }
                }
                Text(stringResource(R.string.appearance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
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
                            Text(stringResource(when (option) { GameTheme.SUNSET -> R.string.theme_sunset; GameTheme.OCEAN -> R.string.theme_ocean; GameTheme.MIDNIGHT -> R.string.theme_midnight }), fontSize = 12.sp, color = option.ink, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            Text(if (active) stringResource(R.string.selected) else stringResource(R.string.select), fontSize = 11.sp, color = if (active) option.accent else option.ink.copy(alpha = .65f))
                        }
                    }
                }
                Text(stringResource(R.string.accessibility), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                Surface(shape = RoundedCornerShape(20.dp), color = selectedTheme.board.copy(alpha = .75f)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SettingsToggle(stringResource(R.string.high_contrast), stringResource(R.string.high_contrast_desc), highContrast, selectedTheme.ink) { highContrast = it; storage.saveHighContrast(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle(stringResource(R.string.large_numbers), stringResource(R.string.large_numbers_desc), largeNumbers, selectedTheme.ink) { largeNumbers = it; storage.saveLargeNumbers(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle(stringResource(R.string.direction_buttons), stringResource(R.string.direction_buttons_desc), directionalControls, selectedTheme.ink) { directionalControls = it; storage.saveDirectionalControls(it) }
                    }
                }
                Text(stringResource(R.string.experience), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = selectedTheme.accent)
                Surface(shape = RoundedCornerShape(20.dp), color = selectedTheme.board.copy(alpha = .75f)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SettingsToggle(stringResource(R.string.sound_effects), stringResource(R.string.sound), soundEnabled, selectedTheme.ink) { soundEnabled = it; storage.saveSoundEnabled(it) }
                        HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        SettingsToggle(stringResource(R.string.vibration), stringResource(R.string.haptic_desc), hapticsEnabled, selectedTheme.ink) { hapticsEnabled = it; storage.saveHapticsEnabled(it) }
                        //HorizontalDivider(color = selectedTheme.ink.copy(alpha = .12f))
                        //SettingsToggle(stringResource(R.string.reduce_motion), stringResource(R.string.reduce_motion_desc), reducedMotion, selectedTheme.ink) { reducedMotion = it; storage.saveReducedMotion(it) }
                    }
                }
            }
        }
    }
    if (confirmRestart) {
        AlertDialog(onDismissRequest = { confirmRestart = false }, title = { Text(stringResource(R.string.restart_confirm)) },
            text = { Text(stringResource(R.string.restart_message)) },
            confirmButton = { TextButton(onClick = { resetMotion(); progress = progress.nextGame(); storage.saveProgress(progress); activity.onNewGameStarted(); game = GameEngine.newGame(); previous = null; rewardedContinue = rewardedContinue.reset(); storage.saveReward(rewardedContinue); winAcknowledged = false; storage.save(game); confirmRestart = false }) { Text(stringResource(R.string.start)) } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } })
    }
    Column(
        Modifier.fillMaxSize().background(selectedTheme.canvas)
            .verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        Text("2048", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = selectedTheme.accent)
        TextButton(onClick = { showModePicker = true }, enabled = !motionActive) { Text(stringResource(when (gameMode) { GameMode.CLASSIC -> R.string.classic; GameMode.TARGET_1024 -> R.string.target_1024; GameMode.ENDLESS -> R.string.endless }) + " ▾", color = selectedTheme.ink) }
        TextButton(onClick = { showLeaderboard = true }) { Text(stringResource(R.string.leaderboard), color = selectedTheme.accent) }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard(stringResource(R.string.score), game.score.toString(), Modifier.weight(1f), selectedTheme)
            ScoreCard(stringResource(R.string.best), best.toString(), Modifier.weight(1f), selectedTheme)
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
                    true,
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
                OutlinedButton(onClick = { performMove(Direction.UP) }, enabled = !motionActive) { Text(stringResource(R.string.up)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { performMove(Direction.LEFT) }, enabled = !motionActive) { Text(stringResource(R.string.left)) }
                    OutlinedButton(onClick = { performMove(Direction.DOWN) }, enabled = !motionActive) { Text(stringResource(R.string.down)) }
                    OutlinedButton(onClick = { performMove(Direction.RIGHT) }, enabled = !motionActive) { Text(stringResource(R.string.right)) }
                }
            }
        }
        Text(stringResource(R.string.swipe_hint), color = selectedTheme.ink)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (doubledGameKey != "${game.score}:${game.board.joinToString(",")}") {
                OutlinedButton(
                    onClick = {
                        previous?.let { restored ->
                            val session = verifiedSession
                            val priorMoves = session?.moves?.dropLast(1)
                            val valid = session == null || (session.moves.isNotEmpty() &&
                                priorMoves != null && VerifiedEngine.replay(session.seed, priorMoves)?.let {
                                    it.board == restored.board && it.score == restored.score
                                } == true)
                            if (valid) {
                                if (session != null && priorMoves != null) {
                                    verifiedSession = session.copy(moves = priorMoves)
                                    storage.saveVerified(verifiedSession)
                                }
                                game = restored
                                previous = null
                                rewardedContinue = rewardedContinue.copy(history = rewardedContinue.history.dropLast(1))
                                storage.saveReward(rewardedContinue)
                                storage.save(game)
                            }
                        }
                    },
                    enabled = previous != null && !motionActive
                ) { Text(stringResource(R.string.undo), fontSize = 12.sp) }
            }
            Button(onClick = { confirmRestart = true }) { Text(stringResource(R.string.new_game), fontSize = 12.sp) }
            OutlinedButton(onClick = { showSettings = true }, enabled = !motionActive) { Text(stringResource(R.string.settings), fontSize = 12.sp) }
        }
        TextButton(onClick = { showProgress = true }, enabled = !motionActive) { Text(stringResource(R.string.stats_achievements)) }
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
private fun AnimatedTile(value: Int, modifier: Modifier = Modifier, reducedMotion: Boolean = true, theme: GameTheme = GameTheme.SUNSET, highContrast: Boolean = false, largeNumbers: Boolean = false, pop: Boolean = false) {
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
            .semantics { contentDescription = if (value == 0) "Empty cell" else "$value tile" },
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
