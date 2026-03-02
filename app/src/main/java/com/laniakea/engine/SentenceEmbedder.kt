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
    private val maxLen: Int = 64
) {
    private var interpreter: Interpreter? = null
    private var wordPieceTokenizer: WordPieceTokenizer? = null
    private val hiddenDim = 768
    private val mutex = Mutex()
    private val _ready = MutableSharedFlow<Boolean>(replay = 1)
    val ready: SharedFlow<Boolean> get() = _ready

    // Privacy setting
    private val LAPLACIAN_NOISE_SCALE = 0.01f
    private var cachedPermutation: IntArray? = null

    fun initialize() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (wordPieceTokenizer == null) {
                    wordPieceTokenizer = WordPieceTokenizer(context, vocabFile)
                }

                val settings = db.diaryDao().getSettings()
                val seed = try {
                    settings?.privacySeed?.let {
                        securityManager.decrypt(it).toInt()
                    } ?: generateNewSeed(settings)
                } catch (_: Exception) {
                    generateNewSeed(settings)
                }
                
                // Fixed permutation ensures that while the vector is scrambled for privacy,
                // it remains consistent for internal mathematical operations (like Vibe calculations).
                cachedPermutation = generatePermutation(seed)

                if (interpreter == null) {
                    val modelBuffer = loadModelFile(modelFile)
                    interpreter = Interpreter(modelBuffer)
                    Log.i("SentenceEmbedder", "Model and Privacy Shield initialized")
                } else {
                    Log.i("SentenceEmbedder", "Privacy Shield updated with new seed")
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
            // --- Tokenize ---
            val tokens = activeTokenizer.tokenize(text, maxLen)
            val inputIds = Array(1) { tokens } // shape [1, maxLen]
            val attentionMask = Array(1) { tokens.map { if (it > 0) 1 else 0 }.toIntArray() }

            // --- Allocate output [1, maxLen, hiddenDim] ---
            val output = Array(1) { Array(maxLen) { FloatArray(hiddenDim) } }

            // --- Run inference ---
            activeInterpreter.runForMultipleInputsOutputs(arrayOf(inputIds, attentionMask), mapOf(0 to output))

            // --- Mean pooling over non-padding tokens ---
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

    /**
     * Applies the Privacy Shield to an embedding.
     * 
     * NOTE ON SHUFFLING: The shuffle step (Step 4) uses a fixed permutation map for the user.
     * This protects against outsiders comparing your vectors to standard models, while 
     * preserving the Dot Product results used in VibeEngine because both the input 
     * and the reference anchors are shuffled identically.
     */
    private fun applyPrivacyShield(vector: FloatArray, doShuffle: Boolean = true): FloatArray {
        // 1. Initial normalization
        val norm = sqrt(vector.fold(0f) { acc, f -> acc + f * f }.toDouble()).toFloat()
        val unitVector = FloatArray(vector.size) { vector[it] / (norm + 1e-9f) }

        // 2. Add Laplace noise (Differential Privacy)
        val noisy = FloatArray(unitVector.size) { i ->
            val u = Random.nextFloat() - 0.5f
            val noise = -LAPLACIAN_NOISE_SCALE * sign(u) *
                    ln(1.0 - 2.0 * abs(u).toDouble()).toFloat()
            unitVector[i] + noise
        }

        // 3. Precision clipping (limits info leakage)
        val clipped = FloatArray(noisy.size) { i -> (noisy[i] * 1000).toInt() / 1000f }

        // 4. Shuffle (Obfuscation)
        val perm = cachedPermutation
        return if (doShuffle && perm != null) {
            FloatArray(clipped.size) { clipped[perm[it]] }
        } else {
            clipped
        }
    }

    // Generates a fixed permutation given a seed
    private fun generatePermutation(seed: Int): IntArray {
        val indices = IntArray(hiddenDim) { it }
        val random = Random(seed)
        for (i in hiddenDim - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            // Swap
            val temp = indices[i]
            indices[i] = indices[j]
            indices[j] = temp
        }
        return indices
    }

    /**
     * Load TFLite model from assets
     */
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

        init {
            // Pre-sizing the HashMap avoids memory spikes during load
            val tempMap = HashMap<String, Int>(30522)
            context.assets.open(vocabFile).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    tempMap[line.trim()] = index
                }
            }
            word2id = tempMap
        }

        fun tokenize(text: String, maxLen: Int): IntArray {
            val result = mutableListOf<Int>()

            // Step 1: Handle punctuation like "hard.life" -> "hard . life"
            val words = text.lowercase()
                .replace(Regex("(\\p{Punct})"), " $1 ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            // Step 2: WordPiece Greedy Matching
            for (word in words) {
                var start = 0
                while (start < word.length) {
                    var end = word.length
                    var curSubwordId: Int? = null

                    while (start < end) {
                        var substr = word.substring(start, end)
                        if (start > 0) substr = "##$substr" // Add suffix marker

                        if (word2id.containsKey(substr)) {
                            curSubwordId = word2id[substr]
                            break
                        }
                        end--
                    }

                    if (curSubwordId == null) {
                        result.add(word2id["[UNK]"] ?: 0)
                        break // Move to next word
                    } else {
                        result.add(curSubwordId)
                        start = end
                    }
                }
            }

            // Step 3: Add special tokens with Truncation Safety
            val clsId = word2id["[CLS]"] ?: 101
            val sepId = word2id["[SEP]"] ?: 102

            // We take maxLen - 2 to leave room for [CLS] at the start and [SEP] at the end
            val limitedResult = if (result.size > maxLen - 2) {
                result.subList(0, maxLen - 2)
            } else {
                result
            }

            val finalTokens = mutableListOf<Int>().apply {
                add(clsId)
                addAll(limitedResult)
                add(sepId)
            }

            // Step 4: Final Padding to exactly maxLen
            return IntArray(maxLen) { i ->
                if (i < finalTokens.size) finalTokens[i] else 0
            }
        }
    }
}