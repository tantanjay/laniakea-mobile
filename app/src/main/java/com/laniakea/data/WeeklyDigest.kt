package com.laniakea.data

data class WeeklyDigest(
    val weekStart: Long,
    val weekEnd: Long,
    val entryCount: Int,
    val activeDays: Int,
    val dominantThemes: List<String>,
    val avgEntryLength: Float,
    val avgEntryLengthPrior: Float?,
    val vocabularyDiversity: Float,
    val vocabularyDiversityPrior: Float?,
    val questionRatio: Float,
    val questionRatioPrior: Float?
)
