package com.laniakea.engine

import android.content.Context
import android.util.Log
import com.laniakea.data.AppSettings
import com.laniakea.data.DiaryDatabase
import com.laniakea.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * SentenceEmbedder generates high-dimensional vectors for text using on-device TFLite.
 * It includes a 'Privacy Shield' to protect sensitive user data.
 */
class SentenceEmbedder(
    private val context: Context,
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val modelFile: String = "sentence_encoder.tflite",
    private val vocabFile: String = "vocab.txt",
    private val maxLen: Int = 256
) {
    private var interpreter: Interpreter? = null
    private var wordPieceTokenizer: WordPieceTokenizer? = null
    private val hiddenDim = 768
    private val mutex = Mutex()
    private val _ready = MutableSharedFlow<Boolean>(replay = 1)
    private var cachedPermutation: IntArray? = null

    val ready: SharedFlow<Boolean> get() = _ready

    init {
        // Initialize tokenizer immediately so quality check works even if core is loading/offline
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wordPieceTokenizer = WordPieceTokenizer(context, vocabFile)
                Log.i("SentenceEmbedder", "WordPieceTokenizer ready for quality checks")
            } catch (e: Exception) {
                Log.e("SentenceEmbedder", "Failed to init tokenizer", e)
            }
        }
    }

    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = db.diaryDao().getSettings()
                val seed = try {
                    settings?.privacySeed?.let {
                        securityManager.decrypt(it).toInt()
                    } ?: generateNewSeed(settings)
                } catch (_: Exception) {
                    generateNewSeed(settings)
                }

                cachedPermutation = generatePermutation(seed)

                if (interpreter == null) {
                    val modelBuffer = loadModelFile(modelFile)
                    interpreter = Interpreter(modelBuffer)
                    Log.i("SentenceEmbedder", "Model and Privacy Shield initialized")
                }

                _ready.emit(true)
            } catch (e: Exception) {
                Log.e("SentenceEmbedder", "Initialize failed", e)
                _ready.emit(false)
            }
        }
    }

    suspend fun embed(text: String): FloatArray? = mutex.withLock {
        val activeInterpreter = interpreter ?: run {
            Log.w("SentenceEmbedder", "Interpreter not ready yet")
            return null
        }

        val activeTokenizer = wordPieceTokenizer ?: run {
            Log.w("SentenceEmbedder", "Tokenizer not ready yet")
            return null
        }

        return try {
            val tokens = activeTokenizer.tokenize(text, maxLen)
            val inputIds = Array(1) { tokens }
            val attentionMask = Array(1) { tokens.map { if (it > 0) 1 else 0 }.toIntArray() }
            val output = Array(1) { Array(maxLen) { FloatArray(hiddenDim) } }

            activeInterpreter.runForMultipleInputsOutputs(arrayOf(inputIds, attentionMask), mapOf(0 to output))

            val sentenceEmbedding = FloatArray(hiddenDim)
            for (i in 0 until hiddenDim) {
                var sum = 0f
                var count = 0
                for (j in 0 until maxLen) {
                    if (attentionMask[0][j] == 1) {
                        sum += output[0][j][i]
                        count++
                    }
                }
                sentenceEmbedding[i] = if (count > 0) sum / count else 0f
            }

            applyPrivacyShield(sentenceEmbedding)
        } catch (e: Exception) {
            Log.e("SentenceEmbedder", "Embedding failed", e)
            null
        }
    }

    fun calculateTokenQuality(text: String): Float {
        return wordPieceTokenizer?.calculateQuality(text) ?: 0.5f
    }

    fun getUsedTokenCount(text: String): Int {
        return wordPieceTokenizer?.countTokens(text) ?: 0
    }

    private fun generateNewSeed(settings: AppSettings?): Int {
        val newSeed = Random.nextInt(1, Int.MAX_VALUE)
        val encrypted = securityManager.encrypt(newSeed.toString())
        CoroutineScope(Dispatchers.IO).launch {
            val finalSettings = settings?.copy(privacySeed = encrypted)
                ?: AppSettings(id = 0, userName = "New User", privacySeed = encrypted)
            db.diaryDao().saveSettings(finalSettings)
        }
        return newSeed
    }

    private fun applyPrivacyShield(vector: FloatArray, doShuffle: Boolean = true): FloatArray {
        val magnitude = sqrt(vector.fold(0f) { acc, f -> acc + f * f }.toDouble()).toFloat()
        if (magnitude < 1e-9f) return vector

        val NOISE_SCALE = 0.002f
        val noisy = FloatArray(vector.size) { i ->
            val u = Random.nextFloat() - 0.5f
            val noise = -NOISE_SCALE * sign(u) * ln(1.0 - 2.0 * abs(u).toDouble()).toFloat()
            vector[i] + noise
        }

        val noisyNorm = sqrt(noisy.fold(0f) { acc, f -> acc + f * f }.toDouble()).toFloat()
        val unitVector = FloatArray(noisy.size) { noisy[it] / (noisyNorm + 1e-9f) }

        val clipped = FloatArray(unitVector.size) { i ->
            (unitVector[i] * 10000).toInt() / 10000f
        }

        val perm = cachedPermutation
        return if (doShuffle && perm != null) {
            FloatArray(clipped.size) { clipped[perm[it]] }
        } else {
            clipped
        }
    }

    private fun generatePermutation(seed: Int): IntArray {
        val indices = IntArray(hiddenDim) { it }
        val random = Random(seed)
        for (i in hiddenDim - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = indices[i]
            indices[i] = indices[j]
            indices[j] = temp
        }
        return indices
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fd = context.assets.openFd(fileName)
        fd.use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).channel.use { fc ->
                return fc.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    class WordPieceTokenizer(context: Context, vocabFile: String) {
        private val word2id: Map<String, Int>
        private val unkId: Int

        init {
            val tempMap = HashMap<String, Int>(30522)
            context.assets.open(vocabFile).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    tempMap[line.trim()] = index
                }
            }
            word2id = tempMap
            unkId = word2id["[UNK]"] ?: 0
        }

        private fun normalize(text: String): String {
            val temp = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            return temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        }

        fun tokenize(text: String, maxLen: Int): IntArray {
            val result = mutableListOf<Int>()
            val normalized = normalize(text)

            val words = normalized
                .replace(Regex("(\\p{Punct})"), " $1 ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            for (word in words) {
                result.addAll(wordpiece(word))
            }

            val clsId = word2id["[CLS]"] ?: 101
            val sepId = word2id["[SEP]"] ?: 102

            val limitedResult = if (result.size > maxLen - 2) result.subList(0, maxLen - 2) else result
            val finalTokens = mutableListOf<Int>().apply {
                add(clsId)
                addAll(limitedResult)
                add(sepId)
            }

            return IntArray(maxLen) { i -> if (i < finalTokens.size) finalTokens[i] else 0 }
        }

        fun countTokens(text: String): Int {
            val result = mutableListOf<Int>()
            val normalized = normalize(text)
            val words = normalized
                .replace(Regex("(\\p{Punct})"), " $1 ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            for (word in words) {
                result.addAll(wordpiece(word))
            }
            return result.size
        }

        private fun wordpiece(word: String): List<Int> {
            val result = mutableListOf<Int>()
            var start = 0
            while (start < word.length) {
                var end = word.length
                var curSubwordId: Int? = null

                while (start < end) {
                    var substr = word.substring(start, end)
                    if (start > 0) substr = "##$substr"

                    if (word2id.containsKey(substr)) {
                        curSubwordId = word2id[substr]
                        break
                    }
                    end--
                }

                if (curSubwordId == null) {
                    result.add(unkId)
                    break
                } else {
                    result.add(curSubwordId)
                    start = end
                }
            }
            return result
        }

        /**
         * Enhanced quality calculation for WordPiece tokenization.
         * Detects garbage text by measuring fragmentation density, spam repetition, and lack of context.
         */
        fun calculateQuality(text: String): Float {
            if (text.isBlank()) return 0f
            val words = normalize(text)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            if (words.isEmpty()) return 0f

            // 1. Context Density Check
            val stopwords = setOf("i", "me", "my", "you", "your", "it", "is", "the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "for", "with", "do", "does", "did", "can", "will", "be", "was", "were", "this", "that")
            val meaningfulWords = words.filter { it !in stopwords }
            val contextDensity = meaningfulWords.size.toFloat() / words.size.toFloat()

            // Normal English is 40-60% stopwords. Only penalize if it's extremely low (e.g., "it is what it is")
            val densityMultiplier = if (words.size > 4 && contextDensity < 0.15f) {
                0.5f // Apply a 50% penalty for lacking substance
            } else {
                1.0f // Normal text, no penalty
            }

            // 2. Heavy Repetition Check & Spam Detection
            val uniqueWords = words.distinct().size
            val varietyRatio = uniqueWords.toFloat() / words.size.toFloat()

            // Count the most frequent word to catch "sad i i i i"
            val wordCounts = words.groupingBy { it }.eachCount()
            val maxWordCount = wordCounts.values.maxOrNull() ?: 0
            val maxWordRatio = maxWordCount.toFloat() / words.size.toFloat()

            val varietyMultiplier = when {
                // If one single word makes up more than 50% of the entry (e.g. "sad i i i i" -> "i" is 80%)
                words.size > 3 && maxWordRatio > 0.5f -> {
                    1.0f - maxWordRatio // Penalty: 80% spam becomes a 0.2 multiplier
                }
                // If they are just mashing a few keys over and over (e.g. "asdf asdf asdf asdf")
                words.size > 5 && varietyRatio < 0.3f -> {
                    varietyRatio
                }
                else -> 1.0f // Normal text
            }

            // 3. Dictionary / Fragmentation Score
            var totalScore = 0f
            for (word in words) {
                if (word2id.containsKey(word)) {
                    totalScore += 1.0f
                } else {
                    val tokens = wordpiece(word)
                    totalScore += if (tokens.contains(unkId) || tokens.isEmpty()) {
                        0.0f
                    } else {
                        val fragRatio = word.length.toFloat() / tokens.size.toFloat()
                        when {
                            fragRatio >= 3.5f -> 0.9f
                            fragRatio >= 2.5f -> 0.6f
                            fragRatio >= 1.5f -> 0.2f
                            else -> 0.0f
                        }
                    }
                }
            }

            val dictionaryScore = totalScore / words.size.toFloat()

            // Final score combinations
            val finalScore = (dictionaryScore * varietyMultiplier * densityMultiplier)

            Log.d("Tokenizer", "Quality for text: Dict=$dictionaryScore, Variety=$varietyMultiplier, Density=$densityMultiplier, Final=$finalScore")
            return finalScore.coerceIn(0f, 1f)
        }

        /**
         * Enhanced quality calculation for WordPiece tokenization.
         * Detects garbage text by measuring fragmentation density and repetitive patterns.
         */
        fun calculateQuality1(text: String): Float {
            if (text.isBlank()) return 0f
            val words = normalize(text)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            if (words.isEmpty()) return 0f

            // Context stop-words to calculate density
            val stopwords = setOf("i", "me", "my", "you", "your", "it", "is", "the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "for", "with", "do", "does", "did", "can", "will", "be", "was", "were", "this", "that")
            val meaningfulWords = words.filter { it !in stopwords }
            val contextDensity = if (words.isNotEmpty()) meaningfulWords.size.toFloat() / words.size.toFloat() else 0f

            // 1. Heavy Repetition Check
            val uniqueWords = words.distinct().size
            val varietyRatio = uniqueWords.toFloat() / words.size.toFloat()

            // If the user types "i i i i i", variety is 0.2.
            // We penalize this heavily if unique words are extremely few.
            val varietyPenalty = if (uniqueWords <= 1 && words.size > 1) 0f else varietyRatio

            var totalScore = 0f
            for (word in words) {
                if (word2id.containsKey(word)) {
                    totalScore += 1.0f
                } else {
                    val tokens = wordpiece(word)
                    totalScore += if (tokens.contains(unkId) || tokens.isEmpty()) {
                        0.0f
                    } else {
                        val fragRatio = word.length.toFloat() / tokens.size.toFloat()
                        when {
                            fragRatio >= 3.5f -> 0.9f
                            fragRatio >= 2.5f -> 0.6f
                            fragRatio >= 1.5f -> 0.2f
                            else -> 0.0f
                        }
                    }
                }
            }

            val dictionaryScore = totalScore / words.size.toFloat()

            // Final score combines dictionary validity, variety, and meaningful context density
            val finalScore = (dictionaryScore * varietyPenalty * (0.5f + 0.5f * contextDensity))

            Log.d("Tokenizer", "Quality for '$text': Dict=$dictionaryScore, Variety=$varietyPenalty, Density=$contextDensity, Final=$finalScore")
            return finalScore.coerceIn(0f, 1f)
        }
    }
}
