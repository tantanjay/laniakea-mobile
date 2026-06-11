package com.laniakea.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 0,
    val privacySeed: String? = null,
    val autoLoadEngine: Boolean = false,
    val userName: String = "",
    val theme: String = "PURPLE",
    val selectedThemes: String = "Relationships & Connection,Career & Purpose,Goals & Ambition,Inner Reflection,Emotional Wellbeing,Physical Wellbeing"
)

@Entity(tableName = "entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    val content: String,
    val mood: String, // potential to iterate but for now mood == numericMood
    val category: String = "",
    val weather: String = "",
    val activities: String = "",
    val numericMood: Double = 0.0,
    val latentVibe: Double = 0.0,
    val isVectorized: Boolean = false,
    val syntacticPacing: Float = 0f,
    val agencyScore: Float = 0f,
    val epistemicModality: Float = 0f,
    val processingMarkers: Int = 0,
    val temporalHorizon: Float = 0f
)