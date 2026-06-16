package com.laniakea.data

data class PeriodDigest(
    val periodStart: Long,
    val periodEnd: Long,
    val entryCount: Int,
    val activeDays: Int,
    val dominantThemes: List<String>,
    val avgEntryLength: Float,
    val avgEntryLengthPrior: Float?,
    val vocabularyDiversity: Float,
    val vocabularyDiversityPrior: Float?,
    val questionRatio: Float,
    val questionRatioPrior: Float?,
    val avgVibeScore: Float? = null,
    val avgVibeScorePrior: Float? = null,
    val avgManualMood: Float? = null,
    val avgManualMoodPrior: Float? = null
)
