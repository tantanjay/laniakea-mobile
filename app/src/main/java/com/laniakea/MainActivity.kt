package com.laniakea

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.engine.VibeEngine
import com.laniakea.ui.theme.LaniakeaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LaniakeaTheme {
                LaniakeaApp()
            }
        }
    }
}

fun mapMoodToValue(mood: String): Double {
    return when (mood.trim()) {
        "Awesome" -> 2.0
        "Good" -> 1.0
        "Fine" -> 0.0
        "Bad" -> -1.0
        "Terrible" -> -2.0
        else -> 0.0
    }
}

suspend fun saveDiaryEntry(
    context: Context,
    content: String,
    mood: String,
    embedding: FloatArray,
    dateTime: Long = System.currentTimeMillis(),
) = withContext(Dispatchers.IO) {
    val db = DiaryDatabase.getDatabase(context)
    val dao = db.diaryDao()

    val aiVibeScore = VibeEngine.calculateVibeScore(embedding)
    val manualMoodScore = mapMoodToValue(mood)

    val entry = DiaryEntry(
        dateTime = dateTime,
        content = content,
        mood = mood,
        numericMood = manualMoodScore,
        latentVibe = aiVibeScore.toDouble()
    )

    dao.insertEntryWithVector(entry, embedding)
    Log.d("DB", "Saved: $mood ($manualMoodScore) | AI Vibe: $aiVibeScore")
}

@Composable
fun EmbeddingTestScreen() {
    val context = LocalContext.current
    val embedder = remember { SentenceEmbedder(context) }
    var inputText by rememberSaveable { mutableStateOf("I feel happy today!") }
    var embeddingResult by remember { mutableStateOf<FloatArray?>(null) }

    var isImporting by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableIntStateOf(0) }
    var totalItems by remember { mutableIntStateOf(0) }

    // Coroutine scope for async execution
    val scope = rememberCoroutineScope()

    val db = remember { DiaryDatabase.getDatabase(context) }
    var momentumData: Triple<Float, String, List<Float>> by remember {
        mutableStateOf(Triple(0f, "STABLE", emptyList()))
    }
    var aiResult: Triple<Float, String, List<Float>> by remember {
        mutableStateOf(Triple(0f, "STABLE", emptyList()))
    }

    LaunchedEffect(Unit) {
        embedder.ready.collect { isReady ->
            if (isReady) {
                VibeEngine.joyAnchor = embedder.embed("I feel incredibly happy, fulfilled, and optimistic.")
                VibeEngine.distressAnchor = embedder.embed("I feel miserable, exhausted, and hopeless.")
                Log.d("VIBE", "Anchors initialized successfully")
            }
        }
    }

    LaunchedEffect(Unit) {
        val entries = db.diaryDao().getAllEntries()

        val manualValues = entries.map { it.numericMood.toFloat() }
        momentumData = calculateMomentum(manualValues)

        val aiValues = entries.map { it.latentVibe.toFloat() }
        aiResult = calculateMomentum(aiValues)

        Log.d(
            "MOMENTUM",
            "Entries: %d : MANUAL : Score: %.6f, Status: %s : AI : Score: %.6f, Status: %s "
                .format(entries.size, momentumData.first, momentumData.second, aiResult.first, aiResult.second)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter text") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            embedder.embedAsync(inputText) { embedding ->
                if (embedding == null) {
                    Log.e("UI", "Embedding failed or model not loaded yet")
                    return@embedAsync
                }

                embeddingResult = embedding

                scope.launch {
                    saveDiaryEntry(context, inputText, "Good", embedding)
                }
            }
        }) {
            Text("Embed Text")
        }

        Button(
            onClick = {
                scope.launch {
                    isImporting = true
                    importCsvData(
                        context = context,
                        embedder = embedder,
                        onProgress = { current, total ->
                            currentProgress = current
                            totalItems = total
                        }
                    )
                    isImporting = false
                }
            },
            enabled = !isImporting
        ) {
            Text(if (isImporting) "Importing..." else "Clear DB & Import dummy.csv")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        MomentumScreen(
            momentumData,
            aiResult
        )

        if (isImporting) {
            Text("Processing: $currentProgress out of $totalItems")
        }

        embeddingResult?.let { vector ->
            Text("Embedding length: ${vector.size}")
            Text(
                "First 10 dims: ${
                    vector.take(10).joinToString(", ") { "%.3f".format(it) }
                }"
            )
        }
    }
}

fun calculateMomentum(values: List<Float>, span: Int = 7): Triple<Float, String, List<Float>> {
    if (values.size < 2) return Triple(0f, "STABLE", emptyList())

    // It doesn't care if these values are Manual labels or AI vibes anymore
    val deltas = mutableListOf(0f)
    for (i in 1 until values.size) {
        deltas.add(values[i] - values[i - 1])
    }

    val alpha = 2f / (span + 1f)
    val trend = mutableListOf<Float>()
    var ema = deltas[0]
    trend.add(ema)

    for (i in 1 until deltas.size) {
        ema = (deltas[i] * alpha) + (ema * (1f - alpha))
        trend.add(ema)
    }

    val score = (ema * 100f).coerceIn(-100f, 100f)
    val status = listOf(
        -20f to "SHARP DECLINE",
        -5f to "DECLINING",
        5f to "STABLE",
        20f to "IMPROVING",
        101f to "STRONG UPTURN"
    ).first { score < it.first }.second

    return Triple(score, status, trend)
}

suspend fun importCsvData(
    context: Context,
    embedder: SentenceEmbedder,
    onProgress: (Int, Int) -> Unit
) = withContext(Dispatchers.IO) {
    val db = DiaryDatabase.getDatabase(context)
    val dao = db.diaryDao()
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    dao.clearDatabase()

    val lines = context.assets.open("dummy.csv").bufferedReader().use { it.readLines() }

    // 1. Filter out the header and empty lines
    val dataLines = lines.filter { it.isNotBlank() && !it.startsWith("date,") }
    val total = dataLines.size

    dataLines.forEachIndexed { index, line ->
        // Use a limit in split to ensure content with commas doesn't break everything
        val parts = line.split(",")
        val (dateString, content, mood) = when {
            line.contains("\"") -> {
                // Handle: 2025-01-14,"Honestly, missing...",Good
                val date = line.substringBefore(",")
                val m = line.substringAfterLast(",").trim()
                val c = line.substringAfter(",").substringBeforeLast(",").trim().removeSurrounding("\"")
                Triple(date, c, m)
            }
            else -> Triple(parts[0].trim(), parts[1].trim(), parts[2].trim())
        }

        // 2. Convert "2025-01-01" to Long timestamp
        val timestamp = try {
            dateFormatter.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }

        val vector = suspendCancellableCoroutine { continuation ->
            embedder.embedAsync(content) { result ->
                continuation.resume(result)
            }
        }

        if (vector != null) {
            saveDiaryEntry(
                context = context,
                content = content,
                mood = mood,
                embedding = vector,
                dateTime = timestamp
            )
        }

        withContext(Dispatchers.Main) {
            onProgress(index + 1, total)
        }
    }
}

@Composable
fun MomentumGauge(
    manualScore: Double,
    manualStatus: String,
    aiScore: Double,
    aiStatus: String
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary focus: The user's input
        Text(
            text = "Perceived Mood: $manualStatus",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "(How you felt)",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Secondary focus: The AI's insight
        Text(
            text = "Latent Sentiment: $aiStatus",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFFBB86FC)
        )
        Text(
            text = "(How you wrote)",
            style = MaterialTheme.typography.bodySmall, // Or a smaller font size
            color = Color(0xFFBB86FC).copy(alpha = 0.7f)
        )

        Canvas(modifier = Modifier.size(280.dp, 150.dp).padding(16.dp)) {
            val strokeWidth = 35f
            val center = Offset(size.width / 2, size.height)
            val radius = size.width / 2

            // 1. DRAW BACKGROUND METER SEGMENTS
            // Total range is 200 units (-100 to 100). Total degrees is 180.
            // Degree per unit = 180 / 200 = 0.9

            // SHARP DECLINE (-100 to -20) -> 80 units * 0.9 = 72 degrees
            drawArc(
                color = Color(0xFFDC143C).copy(alpha = 0.3f), // Crimson
                startAngle = 180f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // DECLINING (-20 to -5) -> 15 units * 0.9 = 13.5 degrees
            drawArc(
                color = Color(0xFFFF4500).copy(alpha = 0.3f), // OrangeRed
                startAngle = 252f,
                sweepAngle = 13.5f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // STABLE (-5 to 5) -> 10 units * 0.9 = 9 degrees
            drawArc(
                color = Color.White.copy(alpha = 0.3f), // Neutral
                startAngle = 265.5f,
                sweepAngle = 9f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // IMPROVING (5 to 20) -> 15 units * 0.9 = 13.5 degrees
            drawArc(
                color = Color(0xFFADFF2F).copy(alpha = 0.3f), // GreenYellow
                startAngle = 274.5f,
                sweepAngle = 13.5f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // STRONG UPTURN (20 to 100) -> 80 units * 0.9 = 72 degrees
            drawArc(
                color = Color(0xFF00FF7F).copy(alpha = 0.3f), // SpringGreen
                startAngle = 288f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // 2. DRAW NEEDLES
            drawNeedle(manualScore, center, radius, Color.Blue, strokeWidth = 10f)
            drawNeedle(aiScore, center, radius * 0.75f, Color(0xFFBB86FC), strokeWidth = 7f)

            // Pivot center
            drawCircle(color = Color.DarkGray, radius = 15f, center = center)
        }
    }
}

fun getDivergenceInsight(
    manualScore: Float,
    manualStatus: String,
    aiScore: Float,
    aiStatus: String
): String {
    return when {
        // Case 1: Perfect Alignment
        manualStatus == aiStatus -> "Your self-perception and writing style are perfectly in sync today."

        // Case 2: Positive Masking (Blue needle high, Purple needle low)
        manualScore > 10 && aiScore < 5 ->
            "Insight: You're pushing for a positive outlook, but your words suggest a more cautious, stable pace."

        // Case 3: Subconscious Optimism (Purple needle higher than Blue)
        aiScore > manualScore + 15 ->
            "Insight: Your writing carries more optimism than you're giving yourself credit for!"

        // Case 4: Critical Divergence (Manual is Good, AI is Bad)
        manualScore > 0 && aiScore < -15 ->
            "Alert: There's a notable gap between your reported mood and your writing context. Consider a self-care break."

        // Default/Neutral
        else -> "You are maintaining a steady balance between your reported feelings and your inner context."
    }
}

@Composable
fun MomentumScreen(manualMomentum: Triple<Float, String, Any>, aiMomentum: Triple<Float, String, Any>) {
    val manualScore = manualMomentum.first
    val aiScore = aiMomentum.first

    val insight = remember(manualScore, aiScore) {
        getDivergenceInsight(manualScore, manualMomentum.second, aiScore, aiMomentum.second)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MomentumGauge(
            manualScore = manualScore.toDouble(),
            manualStatus = manualMomentum.second,
            aiScore = aiScore.toDouble(),
            aiStatus = aiMomentum.second
        )

        Spacer(modifier = Modifier.height(16.dp))

        // THE INSIGHT BOX
        Surface(
            color = Color(0xFFBB86FC).copy(alpha = 0.1f), // Light purple tint
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💡", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Technical Note: While the system successfully identifies divergence between perceived and latent mood, the current model assumes a linear projection of the 768-dimensional space. Future iterations could utilize a personalized calibration layer to account for individual linguistic nuances.",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center
            ),
            color = Color.Gray.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// Helper function to handle the math for needle placement
fun DrawScope.drawNeedle(
    score: Double,
    center: Offset,
    length: Float,
    color: Color,
    strokeWidth: Float
) {
    // Map score (-100..100) to angle (180..360 degrees)
    // -100 is 180 degrees (Left)
    // 0 is 270 degrees (Top)
    // 100 is 360 degrees (Right)
    val angleInDegrees = 180f + ((score + 100f) / 200f * 180f).toFloat()
    val angleInRadians = Math.toRadians(angleInDegrees.toDouble())

    val endX = center.x + length * cos(angleInRadians).toFloat()
    val endY = center.y + length * sin(angleInRadians).toFloat()

    drawLine(
        color = color,
        start = center,
        end = Offset(endX, endY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

@Composable
fun LaniakeaApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> Greeting("Android", Modifier.padding(innerPadding))
                AppDestinations.FAVORITES -> Text("Profile Screen", Modifier.padding(innerPadding))
                AppDestinations.PROFILE -> EmbeddingTestScreen()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.AddCircle),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}