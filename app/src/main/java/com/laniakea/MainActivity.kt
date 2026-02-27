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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laniakea.data.DiaryDatabase
import com.laniakea.data.DiaryEntry
import com.laniakea.engine.SentenceEmbedder
import com.laniakea.ui.theme.LaniakeaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume

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

// Change this function
suspend fun saveDiaryEntry(
    context: Context,
    content: String,
    mood: String,
    embedding: FloatArray,
    dateTime: Long = System.currentTimeMillis(),
) = withContext(Dispatchers.IO) {
    val db = DiaryDatabase.getDatabase(context)
    val dao = db.diaryDao()
    val numericMood: Double = embedding.map { it.toDouble() }.average().coerceIn(-2.0, 2.0)
    val entry = DiaryEntry(
        dateTime = dateTime,
        content = content,
        mood = mood,
        numericMood = numericMood
    )
    dao.insertEntryWithVector(entry, embedding)

    Log.d("DB", "Entry saved successfully")
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
    var momentumData by remember { mutableStateOf(Triple(0.0, "STABLE", emptyList<Double>())) }

    LaunchedEffect(Unit) {
        Log.d("MOMENTUM", "Computing momentum from numericMood...")

        val entries = db.diaryDao().getAllEntries()

        // 1️⃣ Extract numericMood
        val numericMoods = entries.map { it.numericMood }

        // 2️⃣ Compute momentum
        momentumData = calculateMomentumFromNumeric(numericMoods)

        Log.d(
            "MOMENTUM",
            "Score: %.6f, Status: %s".format(momentumData.first, momentumData.second)
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
                // 1. Check if embedding was successful
                if (embedding == null) {
                    Log.e("UI", "Embedding failed or model not loaded yet")
                    return@embedAsync
                }

                // 2. Update UI state
                embeddingResult = embedding

                // 3. Save to DB using the vector we ALREADY generated
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

        // Show the Gauge!
        MomentumGauge(score = momentumData.first, status = momentumData.second)

        // --- Progress Display ---
        if (isImporting) {
            Text("Processing: $currentProgress out of $totalItems")
            // Optional: Add a LinearProgressIndicator here
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

fun calculateMomentumFromNumeric(numericMoods: List<Double>, span: Int = 7): Triple<Double, String, List<Double>> {
    if (numericMoods.size < 2) return Triple(0.0, "STABLE", emptyList())

    // Compute deltas
    val deltas = mutableListOf<Double>()
    for (i in 1 until numericMoods.size) {
        deltas.add(numericMoods[i] - numericMoods[i - 1])
    }

    // EMA: alpha = 2/(span+1)
    val alpha = 2.0 / (span + 1.0)
    var ema = 0.0
    val trend = mutableListOf<Double>()
    deltas.forEach { delta ->
        ema = alpha * delta + (1 - alpha) * ema
        trend.add(ema)
    }

    val score = (ema * 100.0).coerceIn(-100.0, 100.0)

    // Python-style status
    val statusMap = listOf(
        -20.0 to "SHARP DECLINE",
        -5.0 to "DECLINING",
        5.0 to "STABLE",
        20.0 to "IMPROVING",
        101.0 to "STRONG UPTURN"
    )
    val status = statusMap.first { score < it.first }.second

    return Triple(score, status, trend)
}

suspend fun computeMoodNumeric(
    embedder: SentenceEmbedder,
    entry: DiaryEntry
): Double {
    // 1️⃣ Embed the text
    val embedding = embedder.embed(entry.content) ?: return 0.0

    // 2️⃣ Linear projection to numeric mood [-2,2] approximation
    // Python model likely has trained weights; here we simulate with a simple weighted sum
    val weights = DoubleArray(embedding.size) { 1.0 / embedding.size } // uniform weights
    var mood = 0.0
    for (i in embedding.indices) {
        mood += embedding[i].toDouble() * weights[i]
    }

    // 3️⃣ Clip to [-2,2]
    return mood.coerceIn(-2.0, 2.0)
}

fun calculateMomentumPythonStyle(
    entries: List<DiaryEntry>,
    span: Int = 7
): Triple<Float, String, List<Float>> {
    if (entries.size < 2) return Triple(0f, "STABLE", emptyList())

    // 1️⃣ Mood mapping
    val moodMap = mapOf("Terrible" to -2f, "Bad" to -1f, "Fine" to 0f, "Good" to 1f, "Awesome" to 2f)
    val numericMoods = entries.map { moodMap[it.mood] ?: 0f }

    // 2️⃣ Compute day-to-day deltas
    val deltas = mutableListOf<Float>()
    for (i in 1 until numericMoods.size) {
        deltas.add(numericMoods[i] - numericMoods[i - 1])
    }

    // 3️⃣ Compute EMA (alpha = 2 / (span + 1)) like pandas ewm(span=7)
    val alpha = 2f / (span + 1f)  // 2 / (7 + 1) = 0.25
    val trend = mutableListOf<Float>()
    var ema = 0f
    deltas.forEach { delta ->
        ema = alpha * delta + (1 - alpha) * ema
        trend.add(ema)
    }

    // 4️⃣ Scale and clip
    val score = (ema * 100f).coerceIn(-100f, 100f)

    // 5️⃣ Python-style status mapping
    val statusMap = listOf(
        -20f to "SHARP DECLINE",
        -5f to "DECLINING",
        5f to "STABLE",
        20f to "IMPROVING",
        101f to "STRONG UPTURN"
    )

    val status = statusMap.first { score < it.first }.second

    return Triple(score, status, trend)
}

fun calculateMomentum(entries: List<DiaryEntry>): Pair<Float, String> {
    if (entries.size < 2) return 0f to "STABLE"

    val moodMap = mapOf("Terrible" to -2f, "Bad" to -1f, "Fine" to 0f, "Good" to 1f, "Awesome" to 2f)

    // 1. Get numeric values and calculate deltas (differences between days)
    val numericMoods = entries.map { moodMap[it.mood] ?: 0f }
    val deltas = mutableListOf<Float>()
    for (i in 1 until numericMoods.size) {
        deltas.add(numericMoods[i] - numericMoods[i - 1])
    }

    // 2. Simple EMA (Alpha 0.3 is roughly a 7-day span)
    var ema = 0f
    val alpha = 0.3f
    deltas.forEach { delta ->
        ema = alpha * delta + (1 - alpha) * ema
    }

    // 3. Scale and Status
    val score = (ema * 100f).coerceIn(-100f, 100f)
    val status = when {
        score < -20 -> "SHARP DECLINE"
        score < -5 -> "DECLINING"
        score < 5 -> "STABLE"
        score < 20 -> "IMPROVING"
        else -> "STRONG UPTURN"
    }

    return score to status
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

        if (parts.size >= 3) {
            val dateString = parts[0].trim()
            val content = parts[1].trim()
            val mood = parts[2].trim()

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
        }

        withContext(Dispatchers.Main) {
            onProgress(index + 1, total)
        }
    }
}

@Composable
fun MomentumGauge(score: Double, status: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "7-Day Momentum: $status", style = MaterialTheme.typography.titleMedium)

        Canvas(modifier = Modifier.size(250.dp, 130.dp).padding(16.dp)) {
            val strokeWidth = 40f

            // 1. Draw Static Background Segments (The "Meter" background)
            // Left: Crimson (Negative range)
            drawArc(
                color = Color(0xFFDC143C).copy(alpha = 0.2f),
                startAngle = 180f,
                sweepAngle = 45f, // Covers -100 to -50
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // Right: SpringGreen (Positive range)
            drawArc(
                color = Color(0xFF00FF7F).copy(alpha = 0.2f),
                startAngle = 315f,
                sweepAngle = 45f, // Covers 50 to 100
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Butt)
            )

            // 2. Draw the actual Indicator (The "Needle")
            // Map -100..100 to 0..180 degrees
            val indicatorAngle: Double = ((score + 100) / 200) * 180f

            drawArc(
                color = when {
                    score < -20 -> Color(0xFFDC143C)
                    score > 20 -> Color(0xFF00FF7F)
                    else -> Color.White
                },
                startAngle = 180f,
                sweepAngle = indicatorAngle.toFloat(),
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
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