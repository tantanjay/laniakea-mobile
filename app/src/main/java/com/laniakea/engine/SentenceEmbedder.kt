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
    private val maxLen: Int = 256
) {
    private var interpreter: Interpreter? = null
    private var wordPieceTokenizer: WordPieceTokenizer? = null
    private val hiddenDim = 768
    private val mutex = Mutex()
    private val _ready = MutableSharedFlow<Boolean>(replay = 1)
    private var cachedPermutation: IntArray? = null
    private var rotationMatrix: Array<FloatArray>? = null
    private var signMask: FloatArray? = null
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
                signMask = generateSignMask(seed)

                rotationMatrix = generateRotation(seed)

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

            return applyPrivacyShield(sentenceEmbedding)
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

    private fun applyPrivacyShield(vector: FloatArray): FloatArray {

        val NOISE_SCALE = 0.002f

        val noisy = FloatArray(vector.size) { i ->

            val u = Random.nextFloat() - 0.5f
            val noise = -NOISE_SCALE *
                    sign(u) *
                    ln(1.0 - 2.0 * abs(u).toDouble()).toFloat()

            vector[i] + noise
        }

        val normalized = normalize(noisy)

        val mask = signMask ?: return normalized

        val rotated = FloatArray(normalized.size)

        for (i in normalized.indices) {
            rotated[i] = normalized[i] * mask[i]
        }

        return rotated
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

    private fun normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) {
            sum += v * v
        }

        val norm = sqrt(sum.toDouble()).toFloat()
        if (norm < 1e-9f) return vec

        val out = FloatArray(vec.size)
        for (i in vec.indices) {
            out[i] = vec[i] / norm
        }
        return out
    }

    private fun generateRotation(seed: Int): Array<FloatArray> {
        val random = Random(seed)
        val matrix = Array(hiddenDim) { FloatArray(hiddenDim) }

        // random gaussian matrix
        for (i in 0 until hiddenDim) {
            for (j in 0 until hiddenDim) {
                matrix[i][j] = random.nextFloat() - 0.5f
            }
        }

        // Gram-Schmidt orthogonalization
        for (i in 0 until hiddenDim) {

            for (j in 0 until i) {
                var dot = 0f
                for (k in 0 until hiddenDim) {
                    dot += matrix[i][k] * matrix[j][k]
                }

                for (k in 0 until hiddenDim) {
                    matrix[i][k] -= dot * matrix[j][k]
                }
            }

            var norm = 0f
            for (k in 0 until hiddenDim) {
                norm += matrix[i][k] * matrix[i][k]
            }

            norm = sqrt(norm.toDouble()).toFloat()

            for (k in 0 until hiddenDim) {
                matrix[i][k] /= (norm + 1e-9f)
            }
        }

        return matrix
    }

    private fun rotate(vec: FloatArray): FloatArray {
        val rot = rotationMatrix ?: return vec
        val out = FloatArray(vec.size)

        for (i in vec.indices) {
            var sum = 0f
            for (j in vec.indices) {
                sum += rot[i][j] * vec[j]
            }
            out[i] = sum
        }

        return out
    }

    private fun generateSignMask(seed: Int): FloatArray {

        val random = Random(seed)

        return FloatArray(hiddenDim) {
            if (random.nextBoolean()) 1f else -1f
        }
    }
}
