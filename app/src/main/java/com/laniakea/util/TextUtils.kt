package com.laniakea.util

/**
 * Shared text analysis utilities used by both the graph engine (for topic extraction
 * during community naming) and the UI layer (for display fallbacks).
 */

val STOP_WORDS = setOf(
    "i", "me", "my", "myself", "we", "our", "ours", "ourselves", "you", "your", "yours", "yourself", "yourselves",
    "he", "him", "his", "himself", "she", "her", "hers", "herself", "it", "its", "itself", "they", "them", "their",
    "theirs", "themselves", "what", "which", "who", "whom", "this", "that", "these", "those", "am", "is", "are",
    "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did", "doing", "a", "an",
    "the", "and", "but", "if", "or", "because", "as", "until", "while", "of", "at", "by", "for", "with", "about",
    "against", "between", "into", "through", "during", "before", "after", "above", "below", "to", "from", "up",
    "down", "in", "out", "on", "off", "over", "under", "again", "further", "then", "once", "here", "there", "when",
    "where", "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor",
    "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can", "will", "just", "don", "should", "now",
    "really", "feel", "felt", "feeling", "today", "yesterday", "tomorrow", "day", "time", "thing", "things", "much",
    "got", "went", "like", "know", "think", "thought", "see", "make", "made", "good", "bad", "well", "get", "getting",
    "im", "ive", "ill", "id", "dont", "cant", "didnt", "doesnt", "would", "could", "didn", "doesn", "isn", "aren",
    "wasn", "weren", "hasn", "haven", "hadn", "won", "wouldn", "couldn", "shouldn", "might", "must", "may"
)

/**
 * Extracts a human-readable topic label from raw text by finding the most
 * frequent meaningful word (after stop-word removal).
 */
fun extractTopicFromText(text: String): String {
    if (text.isBlank()) return "Unknown Thought"
    
    val words = text.lowercase()
        .split(Regex("[^a-z]+"))
        .filter { it.length > 2 && !STOP_WORDS.contains(it) }
    
    if (words.isEmpty()) {
        val fallbackWords = text.split(Regex("\\W+")).filter { it.length > 2 }
        val fallbackWord = fallbackWords.maxByOrNull { it.length }?.replaceFirstChar { it.uppercase() }
        return if (fallbackWord != null) "$fallbackWord Thought" else "Unknown Thought"
    }
    
    val frequencies = words.groupingBy { it }.eachCount()
    
    val bestWord = frequencies.maxWithOrNull(Comparator { a, b ->
        val freqDiff = a.value.compareTo(b.value)
        if (freqDiff != 0) freqDiff else a.key.length.compareTo(b.key.length)
    })?.key
    
    val topic = bestWord?.replaceFirstChar { it.uppercase() }
    return if (topic != null) "$topic Thought" else "Unknown Thought"
}

/**
 * Maps a numeric mood score to a human-readable emoji + label.
 * Centralized here to eliminate duplication across MapScreen, NodeDetailPanel, and Canvas labels.
 */
fun getMoodLabel(moodScore: Double): String {
    return when {
        moodScore > 1.5 -> "🤩 Awesome"
        moodScore > 0.5 -> "🙂 Good"
        moodScore > -0.5 -> "😐 Fine"
        moodScore > -1.5 -> "🙁 Bad"
        else -> "😫 Terrible"
    }
}
