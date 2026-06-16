package com.laniakea.util

object QuestionnaireUtils {
    /**
     * Generates a synthetic sentence block from questionnaire answers.
     * This text is embedded by the NLP engine so the entry correctly maps into the semantic constellation.
     * 
     * Output format example:
     * "Today energy was drained. Primary theme was work. Mental pace was overwhelmed. Social state was isolated. Thinking style was reflecting. Temporal focus was future. Intensity was heavy."
     */
    fun generateSyntheticSentence(
        energy: String,
        theme: String,
        mentalPace: String,
        socialState: String,
        thinkingStyle: String,
        temporalFocus: String,
        intensity: String
    ): String {
        return buildString {
            if (energy.isNotBlank()) append("Today energy was ${energy.lowercase()}. ")
            if (theme.isNotBlank()) append("Primary theme was ${theme.lowercase()}. ")
            if (mentalPace.isNotBlank()) append("Mental pace was ${mentalPace.lowercase()}. ")
            if (socialState.isNotBlank()) append("Social state was ${socialState.lowercase()}. ")
            if (thinkingStyle.isNotBlank()) append("Thinking style was ${thinkingStyle.lowercase()}. ")
            if (temporalFocus.isNotBlank()) append("Temporal focus was ${temporalFocus.lowercase()}. ")
            if (intensity.isNotBlank()) append("Intensity was ${intensity.lowercase()}. ")
        }.trim()
    }

    /**
     * Generates a compact summary string for UI display (e.g. in JournalScreen) 
     * instead of showing the full, verbose synthetic sentence.
     */
    fun generateCompactDisplay(entry: com.laniakea.data.DiaryEntry): String {
        val parts = mutableListOf<String>()
        entry.mainTheme?.let { parts.add(it) }
        val energy = mapFloatToEnergy(entry.energyLevel)
        if (energy != "Unknown") parts.add("$energy Energy")
        val pace = mapFloatToMentalPace(entry.mentalPace)
        if (pace != "Unknown") parts.add("$pace Mind")
        val social = mapFloatToSocialState(entry.connectionLevel)
        if (social != "Unknown") parts.add(social)
        return parts.joinToString(" • ")
    }

    // --- Options Lists ---
    val energyOptions = listOf("Very High", "High", "Balanced", "Low", "Drained")
    val themeOptions = listOf("Work / Study", "Relationships", "Family", "Health", "Learning", "Goals & Future", "Creativity", "Personal Growth", "Daily Life", "Other")
    val mentalPaceOptions = listOf("Calm", "Focused", "Racing", "Sluggish", "Overwhelmed")
    val socialStateOptions = listOf("Deeply Connected", "Social", "Balanced", "Solitary", "Isolated")
    val thinkingStyleOptions = listOf("Planning", "Reflecting", "Daydreaming", "Problem Solving", "Ruminating")
    val temporalFocusOptions = listOf("Past", "Present", "Future")
    val intensityOptions = listOf("Light", "Moderate", "Heavy")

    // --- Mappers ---
    // String to Float (for DB storage)
    fun mapEnergyToFloat(energy: String): Float? =
        when (energy) { "Very High" -> 5f; "High" -> 4f; "Balanced" -> 3f; "Low" -> 2f; "Drained" -> 1f; else -> null }
        
    fun mapMentalPaceToFloat(pace: String): Float? =
        when (pace) { "Racing" -> 5f; "Overwhelmed" -> 4f; "Focused" -> 3f; "Calm" -> 2f; "Sluggish" -> 1f; else -> null }
        
    fun mapSocialStateToFloat(state: String): Float? =
        when (state) { "Deeply Connected" -> 5f; "Social" -> 4f; "Balanced" -> 3f; "Solitary" -> 2f; "Isolated" -> 1f; else -> null }
        
    fun mapIntensityToFloat(intensity: String): Float? =
        when (intensity) { "Heavy" -> 3f; "Moderate" -> 2f; "Light" -> 1f; else -> null }

    // Float to String (for UI rendering)
    fun mapFloatToEnergy(value: Float?): String =
        when (value) { 5f -> "Very High"; 4f -> "High"; 3f -> "Balanced"; 2f -> "Low"; 1f -> "Drained"; else -> "Unknown" }
        
    fun mapFloatToMentalPace(value: Float?): String =
        when (value) { 5f -> "Racing"; 4f -> "Overwhelmed"; 3f -> "Focused"; 2f -> "Calm"; 1f -> "Sluggish"; else -> "Unknown" }
        
    fun mapFloatToSocialState(value: Float?): String =
        when (value) { 5f -> "Deeply Connected"; 4f -> "Social"; 3f -> "Balanced"; 2f -> "Solitary"; 1f -> "Isolated"; else -> "Unknown" }
        
    fun mapFloatToIntensity(value: Float?): String =
        when (value) { 3f -> "Heavy"; 2f -> "Moderate"; 1f -> "Light"; else -> "Unknown" }
}
