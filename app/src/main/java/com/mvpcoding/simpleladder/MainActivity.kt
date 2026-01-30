package com.mvpcoding.simpleladder

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // AdMob 초기화
        MobileAds.initialize(this) { loadRewardedAd() }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LadderGameStage2Screen(
                        onShowAd = { onRewardEarned ->
                            showRewardedAd(onRewardEarned)
                        }
                    )
                }
            }
        }
    }

    private fun loadRewardedAd() {
        if (isAdLoading || rewardedAd != null) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
                isAdLoading = false
            }
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                isAdLoading = false
            }
        })
    }

    private fun showRewardedAd(onRewardEarned: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(this) {
                onRewardEarned()
                rewardedAd = null
                loadRewardedAd()
            }
        } ?: run {
            onRewardEarned() // 광고 없으면 그냥 통과
            loadRewardedAd()
        }
    }
}

enum class Language { KR, EN }

class Translation(val lang: Language) {
    val title = if (lang == Language.KR) "사다리게임" else "Ladder Game"
    val totalPlayers = if (lang == Language.KR) "총 인원(최대12명)" else "Total Players (Max 12)"
    val winners = if (lang == Language.KR) "당첨 수" else "Winners"
    val losers = if (lang == Language.KR) "꽝 수" else "Losers"
    val createLadder = if (lang == Language.KR) "사다리 생성" else "Create"
    val reset = if (lang == Language.KR) "초기화" else "Reset"
    val selectStart = if (lang == Language.KR) "출발 번호 선택" else "Select Start"
    val start = if (lang == Language.KR) "시작" else "Start"
    val statusIdle = if (lang == Language.KR) "출발 번호를 선택하고 '시작'을 누르세요" else "Select a number and press 'Start'"
    val statusAnimating = if (lang == Language.KR) "사다리 타는 중..." else "Climbing..."
    val resultPrefix = if (lang == Language.KR) "결과: " else "Result: "
    val win = if (lang == Language.KR) "당첨" else "WIN"
    val lose = if (lang == Language.KR) "꽝" else "LOSE"
    val settings = if (lang == Language.KR) "설정" else "Settings"
    val selectLang = if (lang == Language.KR) "언어 선택" else "Language"
    val versionInfo = if (lang == Language.KR) "버전 정보" else "Version Info"
    val close = if (lang == Language.KR) "닫기" else "Close"
}

data class LadderRung(val yIndex: Int, val leftLine: Int)
data class TracePoint(val xLine: Int, val yIndex: Int)

@Composable
fun LadderGameStage2Screen(onShowAd: (() -> Unit) -> Unit) {
    var language by remember { mutableStateOf(Language.KR) }
    val t = remember(language) { Translation(language) }
    var showSettings by remember { mutableStateOf(false) }

    var playerCount by remember { mutableIntStateOf(4) }
    var winnerCount by remember { mutableIntStateOf(1) }
    var loserCount by remember { mutableIntStateOf(3) }

    var players by remember { mutableStateOf(listOf<String>()) }
    var mapping by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    var rungs by remember { mutableStateOf(listOf<LadderRung>()) }
    val levels = 20
    var selectedPlayerIndex by remember { mutableIntStateOf(0) }

    var trace by remember { mutableStateOf(listOf<TracePoint>()) }
    var animT by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var finalLineIndex by remember { mutableIntStateOf(-1) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(playerCount) {
        if (winnerCount + loserCount > playerCount) {
            winnerCount = 1
            loserCount = (playerCount - 1).coerceAtLeast(0)
        }
    }

    suspend fun playAnimation(newTrace: List<TracePoint>, endLine: Int) {
        trace = newTrace
        finalLineIndex = -1
        animT = 0f
        isAnimating = true
        val steps = 100
        for (i in 0..steps) {
            animT = i / steps.toFloat()
            delay(10)
        }
        finalLineIndex = endLine
        isAnimating = false
        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CounterRow(t.totalPlayers, playerCount, 2, 12) { playerCount = it }
                CounterRow(t.winners, winnerCount, 0, playerCount - loserCount) { winnerCount = it }
                CounterRow(t.losers, loserCount, 0, playerCount - winnerCount) { loserCount = it }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                onShowAd {
                    val p = (1..playerCount).map { it.toString() }
                    val res = mutableListOf<String>()
                    repeat(winnerCount) { res.add("WIN") }
                    repeat(loserCount) { res.add("LOSE") }
                    repeat(playerCount - winnerCount - loserCount) { res.add("-") }
                    players = p
                    mapping = p.zip(res.shuffled(Random(System.currentTimeMillis())))
                    rungs = generateRungs(p.size, levels, 0.7f, System.currentTimeMillis())
                    selectedPlayerIndex = 0
                    trace = emptyList()
                    animT = 0f
                    finalLineIndex = -1
                }
            }) { Text(t.createLadder) }

            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                players = emptyList()
                mapping = emptyList()
                finalLineIndex = -1
            }) { Text(t.reset) }
        }

        if (players.isNotEmpty()) {
            HorizontalDivider()
            Text(t.selectStart, style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(players) { idx, name ->
                    FilterChip(
                        selected = (idx == selectedPlayerIndex),
                        onClick = { if (!isAnimating) selectedPlayerIndex = idx },
                        label = { Text("${name}${if(language == Language.KR) "번" else ""}") }
                    )
                }
            }

            Button(modifier = Modifier.fillMaxWidth(), enabled = !isAnimating, onClick = {
                val (tr, end) = computeTraceAndEndLine(selectedPlayerIndex, players.size, levels, rungs)
                scope.launch { playAnimation(tr, end) }
            }) { Text(t.start) }

            LadderGameArea(
                modifier = Modifier.height(360.dp).fillMaxWidth(),
                players = players,
                results = mapping.map { if(it.second == "WIN") t.win else if(it.second == "LOSE") t.lose else "-" },
                rungs = rungs,
                levels = levels,
                trace = trace,
                animT = animT,
                highlightPath = isAnimating || finalLineIndex >= 0
            )

            Card(modifier = Modifier.fillMaxWidth().height(80.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    val statusText = when {
                        isAnimating -> t.statusAnimating
                        finalLineIndex >= 0 -> {
                            val key = mapping[finalLineIndex].second
                            t.resultPrefix + (if(key == "WIN") t.win else if(key == "LOSE") t.lose else "-")
                        }
                        else -> t.statusIdle
                    }
                    Text(statusText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(t.settings) },
            text = {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(t.versionInfo, style = MaterialTheme.typography.labelMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(t.selectLang, style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = language == Language.KR, onClick = { language = Language.KR })
                        Text("한국어", modifier = Modifier.clickable { language = Language.KR })
                        Spacer(modifier = Modifier.width(20.dp))
                        RadioButton(selected = language == Language.EN, onClick = { language = Language.EN })
                        Text("English", modifier = Modifier.clickable { language = Language.EN })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text(t.close) } }
        )
    }
}

@Composable
fun CounterRow(label: String, value: Int, min: Int, max: Int, onValueChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onValueChange(value - 1) }) {
                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(value.toString(), modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (value < max) onValueChange(value + 1) }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    }
}

@Composable
fun LadderGameArea(
    modifier: Modifier,
    players: List<String>,
    results: List<String>,
    rungs: List<LadderRung>,
    levels: Int,
    trace: List<TracePoint>,
    animT: Float,
    highlightPath: Boolean
) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    players.forEach { Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, fontWeight = FontWeight.Bold, color = Color.Blue) } }
                }
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val count = players.size
                    val slotW = size.width / count
                    fun getX(i: Int) = (i + 0.5f) * slotW
                    val dy = size.height / levels
                    for (i in 0 until count) drawLine(Color.LightGray, Offset(getX(i), 0f), Offset(getX(i), size.height), strokeWidth = 2f)
                    for (r in rungs) drawLine(Color.Gray, Offset(getX(r.leftLine), r.yIndex * dy), Offset(getX(r.leftLine + 1), r.yIndex * dy), strokeWidth = 4f)
                    if (highlightPath && trace.isNotEmpty()) {
                        val points = trace.map { Offset(getX(it.xLine), it.yIndex * dy) }
                        val path = Path().apply { moveTo(points[0].x, points[0].y); for (i in 1 until points.size) lineTo(points[i].x, points[i].y) }
                        drawPath(path, Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                        drawCircle(Color.Magenta, center = interpolateAlongPolyline(points, animT), radius = 10f)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    results.forEach { Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(it, fontSize = 10.sp, fontWeight = FontWeight.Medium) } }
                }
            }
        }
    }
}

private fun interpolateAlongPolyline(points: List<Offset>, t: Float): Offset {
    if (points.isEmpty()) return Offset.Zero
    if (points.size == 1) return points[0]
    val segLens = mutableListOf<Float>()
    var total = 0f
    for (i in 0 until points.size - 1) {
        val len = (points[i + 1] - points[i]).getDistance()
        segLens.add(len)
        total += len
    }
    if (total <= 0f) return points.first()
    var target = total * t
    for (i in 0 until segLens.size) {
        if (target <= segLens[i]) return points[i] + (points[i + 1] - points[i]) * (if (segLens[i] == 0f) 0f else target / segLens[i])
        target -= segLens[i]
    }
    return points.last()
}

private fun generateRungs(lines: Int, levels: Int, density: Float, seed: Long): List<LadderRung> {
    val rnd = Random(seed)
    val out = mutableListOf<LadderRung>()
    for (y in 1 until levels) {
        if (rnd.nextFloat() < density) {
            val candidates = (0 until lines - 1).shuffled(rnd)
            val used = BooleanArray(lines - 1)
            for (left in candidates) {
                if (!used[left] && (left - 1 < 0 || !used[left - 1]) && (left + 1 >= used.size || !used[left + 1])) {
                    out.add(LadderRung(y, left))
                    used[left] = true
                }
            }
        }
    }
    return out.sortedBy { it.yIndex }
}

private fun computeTraceAndEndLine(startLine: Int, lines: Int, levels: Int, rungs: List<LadderRung>): Pair<List<TracePoint>, Int> {
    val rungMap = rungs.groupBy { it.yIndex }
    var x = startLine
    val trace = mutableListOf(TracePoint(x, 0))
    for (y in 1..levels) {
        trace.add(TracePoint(x, y))
        val moveRight = (rungMap[y] ?: emptyList()).any { it.leftLine == x }
        val moveLeft = (rungMap[y] ?: emptyList()).any { it.leftLine == x - 1 }
        val newX = when {
            moveRight && x < lines - 1 -> x + 1
            moveLeft && x > 0 -> x - 1
            else -> x
        }
        if (newX != x) { trace.add(TracePoint(newX, y)); x = newX }
    }
    return trace to x
}
