package com.modern2048.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Canvas = Color(0xFFFAF8F5)
private val Ink = Color(0xFF24243A)
private val Board = Color(0xFFE9E5E2)
private val Empty = Color(0xFFF5F1ED)
private val Purple = Color(0xFF6657E8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GameScreen() } }
    }
}

@Composable
private fun GameScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("2048", fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Purple)
        Text("Modern Puzzle", color = Ink)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreCard("SKOR", "0", Modifier.weight(1f))
            ScoreCard("REKOR", "0", Modifier.weight(1f))
        }
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth().background(Board, RoundedCornerShape(24.dp)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(4) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f)
                                .background(Empty, RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Kaydırarak aynı sayıları birleştir", color = Ink)
        Spacer(Modifier.height(24.dp))
        Button(onClick = {}, enabled = false) { Text("Yeni Oyun — Faz 1'de aktif") }
    }
}

@Composable
private fun ScoreCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color.White, RoundedCornerShape(20.dp)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}
