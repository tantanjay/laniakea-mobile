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
    private val maxLen: Int = 256 // Increased from 64 to 256 to support ~180-200 words fully
) {
    private var interpreter: Interpreter? = null
    private var wordPieceTokenizer: WordPieceTokenizer? = null
    private val hiddenDim = 768
    private val mutex = Mutex()
    private val _ready = MutableSharedFlow<Boolean>(replay = 1)
    private var cachedPermutation: IntArray? = null

    val ready: SharedFlow<Boolean> get() = _ready

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
                    // Ensure the interpreter allows dynamic resizing if the model supports it, 
                    // or handles the larger fixed buffer.
                    interpreter = Interpreter(modelBuffer)
                    Log.i("SentenceEmbedder", "Model and Privacy Shield initialized with maxLen $maxLen")
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
            Log.e("SentenceEmbedder", "Embedding failed. If this is a shape error, the TFLite model might have a fixed 64-token limit.", e)
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
     * Applies the Privacy Shield to a text embedding to prevent model inversion attacks.
     */
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

        init {
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

            val words = text.lowercase()
                .replace(Regex("(\\p{Punct})"), " $1 ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }

            for (word in words) {
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
                        result.add(word2id["[UNK]"] ?: 0)
                        break
                    } else {
                        result.add(curSubwordId)
                        start = end
                    }
                }
            }

            val clsId = word2id["[CLS]"] ?: 101
            val sepId = word2id["[SEP]"] ?: 102

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

            return IntArray(maxLen) { i ->
                if (i < finalTokens.size) finalTokens[i] else 0
            }
        }
    }
}
