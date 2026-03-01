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
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

class SentenceEmbedder(
    private val context: Context,
    private val db: DiaryDatabase,
    private val securityManager: SecurityManager,
    private val modelFile: String = "sentence_encoder.tflite",
    vocabFile: String = "vocab.txt",
    private val maxLen: Int = 64
) {
    private var interpreter: Interpreter? = null
    private val tokenizer: Tokenizer = Tokenizer(context, vocabFile)
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
                if (interpreter != null) return@launch

                val settings = db.diaryDao().getSettings()
                val seed = try {
                    settings?.privacySeed?.let {
                        securityManager.decrypt(it).toInt()
                    } ?: generateNewSeed(settings)
                } catch (_: Exception) {
                    generateNewSeed(settings)
                }
                cachedPermutation = generatePermutation(seed)

                val modelBuffer = loadModelFile(modelFile)
                interpreter = Interpreter(modelBuffer)

                Log.i("SentenceEmbedder", "Model and Privacy Shield initialized")

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

        return try {
            // --- Tokenize ---
            val tokens = tokenizer.tokenize(text, maxLen)
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

    private fun applyPrivacyShield(vector: FloatArray, doShuffle: Boolean = true): FloatArray {
        // 1. Initial normalization
        val norm = sqrt(vector.fold(0f) { acc, f -> acc + f * f }.toDouble()).toFloat()
        val unitVector = FloatArray(vector.size) { vector[it] / (norm + 1e-9f) }

        // 2. Add Laplace noise
        val noisy = FloatArray(unitVector.size) { i ->
            val u = Random.nextFloat() - 0.5f
            val noise = -LAPLACIAN_NOISE_SCALE * sign(u) *
                    ln(1.0 - 2.0 * abs(u).toDouble()).toFloat()
            unitVector[i] + noise
        }

        // 3. Precision clipping (3 decimal places)
        val clipped = FloatArray(noisy.size) { i -> (noisy[i] * 1000).toInt() / 1000f }

        // 4. Shuffle (Only if we have a permutation and doShuffle is true)
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

    /**
     * Simple tokenizer using vocab.txt
     */
    class Tokenizer(context: Context, vocabFile: String) {

        private val word2id: Map<String, Int>

        init {
            word2id = loadVocab(context, vocabFile)
        }

        fun tokenize(text: String, maxLen: Int): IntArray {
            // 1. Lowercase & split on non-word characters (anything except letters/numbers)
            val words = text
                .lowercase()
                .split(Regex("[^a-z0-9]+")) // split on punctuation, multiple symbols treated as one
                .filter { it.isNotBlank() }  // remove empty strings

            // 2. Map to vocab, unknown words → 0
            val tokens = words.map { word2id[it] ?: 0 }.toMutableList()

            // 3. Pad or truncate to maxLen
            if (tokens.size > maxLen) tokens.subList(maxLen, tokens.size).clear()
            else if (tokens.size < maxLen) repeat(maxLen - tokens.size) { tokens.add(0) }

            return tokens.toIntArray()
        }

        private fun loadVocab(context: Context, vocabFile: String): Map<String, Int> {
            val map = mutableMapOf<String, Int>()
            context.assets.open(vocabFile).use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.forEachLineIndexed { idx, line ->
                        val word = line.trim()
                        if (word.isNotEmpty()) map[word] = idx
                    }
                }
            }
            return map
        }

        private inline fun BufferedReader.forEachLineIndexed(crossinline action: (Int, String) -> Unit) {
            var index = 0
            this.forEachLine { line ->
                action(index++, line)
            }
        }
    }
}