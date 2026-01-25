package com.mvpcoding.simpleladder

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // AdMob 초기화
        MobileAds.initialize(this) {}
        loadRewardedAd()

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
        val adRequest = AdRequest.Builder().build()
        // 구글 공식 테스트 보상형 광고 ID
        RewardedAd.load(this, "ca-app-pub-3940256099942544/5224354917", adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
        })
    }

    private fun showRewardedAd(onRewardEarned: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(this) {
                // 사용자가 광고를 끝까지 시청함
                onRewardEarned()
                loadRewardedAd() // 다음 광고 미리 로드
            }
        } ?: run {
            Toast.makeText(this, "광고를 준비 중입니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }
}

data class LadderRung(val yIndex: Int, val leftLine: Int)
data class TracePoint(val xLine: Int, val yIndex: Int)

@Composable
fun LadderGameStage2Screen(onShowAd: (() -> Unit) -> Unit) {
    var playerCount by remember { mutableIntStateOf(4) }
    var winnerCount by remember { mutableIntStateOf(1) }
    var loserCount by remember { mutableIntStateOf(3) }
    
    var players by remember { mutableStateOf(listOf<String>()) }
    var mapping by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    var rungs by remember { mutableStateOf(listOf<LadderRung>()) }
    var levels by remember { mutableIntStateOf(20) }
    var selectedPlayerIndex by remember { mutableIntStateOf(0) }

    var trace by remember { mutableStateOf(listOf<TracePoint>()) }
    var animT by remember { mutableFloatStateOf(0f) }
    var isAnimating by remember { mutableStateOf(false) }
    var finalLineIndex by remember { mutableIntStateOf(-1) }

    // 광고 팝업 관련 상태
    var showAdPopup by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    // 화면 스크롤 상태 정의
    val scrollState = rememberScrollState()

    // 인원수 변경 시 당첨/꽝 수 자동 조절
    LaunchedEffect(playerCount) {
        if (winnerCount + loserCount > playerCount) {
            winnerCount = 1
            loserCount = (playerCount - 1).coerceAtLeast(0)
            if (winnerCount + loserCount > playerCount) loserCount = 0
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
        
        // 애니메이션 종료 시 결과창이 보이도록 아래로 자동 스크롤 (Samsung A15 등 작은 화면 대응)
        scope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // 세로 스크롤 추가
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("사다리게임", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // 설정 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CounterRow("총 인원(최대12명)", playerCount, 2, 12) { playerCount = it }
                CounterRow("당첨 수", winnerCount, 0, playerCount - loserCount) { winnerCount = it }
                CounterRow("꽝 수", loserCount, 0, playerCount - winnerCount) { loserCount = it }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val p = (1..playerCount).map { it.toString() }
                    val res = mutableListOf<String>()
                    repeat(winnerCount) { res.add("당첨") }
                    repeat(loserCount) { res.add("꽝") }
                    repeat(playerCount - winnerCount - loserCount) { res.add("-") }
                    
                    val shuffledRes = res.shuffled(Random(System.currentTimeMillis()))
                    players = p
                    mapping = p.zip(shuffledRes)

                    rungs = generateRungs(p.size, levels, 0.7f, System.currentTimeMillis())
                    selectedPlayerIndex = 0
                    trace = emptyList()
                    animT = 0f
                    finalLineIndex = -1
                    showAdPopup = true
                }
            ) { Text("사다리 생성") }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    players = emptyList()
                    mapping = emptyList()
                    rungs = emptyList()
                    trace = emptyList()
                    finalLineIndex = -1
                    showAdPopup = false
                }
            ) { Text("초기화") }
        }

        if (players.isNotEmpty()) {
            HorizontalDivider()
            
            Text("출발 번호 선택", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(players) { idx, name ->
                    FilterChip(
                        selected = (idx == selectedPlayerIndex),
                        onClick = { if (!isAnimating) selectedPlayerIndex = idx },
                        label = { Text("${name}번") }
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnimating,
                onClick = {
                    val (t, endLine) = computeTraceAndEndLine(selectedPlayerIndex, players.size, levels, rungs)
                    scope.launch { playAnimation(t, endLine) }
                }
            ) { Text("시작") }

            // 사다리 게임판 (고정 높이 부여)
            LadderGameArea(
                modifier = Modifier.height(320.dp).fillMaxWidth(),
                players = players,
                results = mapping.map { it.second },
                rungs = rungs,
                levels = levels,
                trace = trace,
                animT = animT,
                highlightPath = isAnimating || finalLineIndex >= 0,
                showAdPopup = showAdPopup,
                onWatchAdClick = {
                    onShowAd {
                        showAdPopup = false
                    }
                }
            )

            // 결과 표시 영역 (항상 고정된 카드로 아래쪽에 표시)
            Card(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    val displayText = when {
                        isAnimating -> "사다리 타는 중..."
                        finalLineIndex >= 0 -> "결과: ${mapping[finalLineIndex].second}"
                        else -> "출발 번호를 선택하고 '시작'을 누르세요"
                    }
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // 하단 스크롤 여유 공간
            Spacer(modifier = Modifier.height(24.dp))
        }
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
            Text(text = value.toString(), modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
    highlightPath: Boolean,
    showAdPopup: Boolean,
    onWatchAdClick: () -> Unit
) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(Color.White).padding(vertical = 8.dp)) {
                // 상단 번호
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    players.forEach {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(it, fontWeight = FontWeight.Bold, color = Color.Blue)
                        }
                    }
                }
                
                // 사다리 캔버스
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val count = players.size
                    val w = size.width
                    val h = size.height
                    val slotW = w / count
                    fun getX(index: Int) = (index + 0.5f) * slotW
                    val dy = h / levels

                    // 세로선
                    for (i in 0 until count) {
                        val x = getX(i)
                        drawLine(Color.LightGray, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
                    }

                    // 가로선
                    for (r in rungs) {
                        val x1 = getX(r.leftLine)
                        val x2 = getX(r.leftLine + 1)
                        val y = r.yIndex * dy
                        drawLine(Color.Gray, Offset(x1, y), Offset(x2, y), strokeWidth = 4f)
                    }

                    // 경로
                    if (highlightPath && trace.isNotEmpty()) {
                        val points = trace.map { Offset(getX(it.xLine), it.yIndex * dy) }
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                        }
                        drawPath(path, Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                        
                        val marker = interpolateAlongPolyline(points, animT)
                        drawCircle(Color.Magenta, center = marker, radius = 10f)
                    }
                }

                // 하단 결과
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    results.forEach {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // 광고 팝업 레이어
            if (showAdPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f))
                        .clickable { onWatchAdClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(1f)
                            .fillMaxHeight(0.6f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RectangleShape
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "광고를 보면 창이 사라집니다.",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "(이곳을 클릭하여 광고 시청)",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
        if (target <= segLens[i]) {
            val u = if (segLens[i] == 0f) 0f else target / segLens[i]
            return points[i] + (points[i + 1] - points[i]) * u
        }
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
    val trace = mutableListOf<TracePoint>()
    trace.add(TracePoint(x, 0))
    for (y in 1..levels) {
        trace.add(TracePoint(x, y))
        val rungsHere = rungMap[y] ?: emptyList()
        val moveRight = rungsHere.any { it.leftLine == x }
        val moveLeft = rungsHere.any { it.leftLine == x - 1 }
        val newX = when {
            moveRight && x < lines - 1 -> x + 1
            moveLeft && x > 0 -> x - 1
            else -> x
        }
        if (newX != x) {
            trace.add(TracePoint(newX, y))
            x = newX
        }
    }
    return trace to x
}
