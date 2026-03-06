package com.laniakea.engine

import android.content.Context
import android.util.Log
import java.text.Normalizer

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

}