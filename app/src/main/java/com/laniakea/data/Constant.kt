package com.laniakea.data

data class Alias(
    val name: String,
    val tags: List<String>
)

object Aliases {

    val ALL = listOf(
        Alias("Stardust", listOf("cosmic", "poetic")),
        Alias("Nebula", listOf("cosmic", "stellar")),
        Alias("Cosmos", listOf("cosmic", "abstract")),
        Alias("Voyager", listOf("spacecraft", "exploration")),
        Alias("Nova", listOf("stellar", "energy")),

        Alias("Quasar", listOf("astronomy", "high-energy")),
        Alias("Zenith", listOf("abstract", "peak")),
        Alias("Aether", listOf("mythic", "ethereal")),
        Alias("Lumen", listOf("light", "poetic")),
        Alias("Solstice", listOf("astronomy", "cycle")),

        Alias("Eclipse", listOf("astronomy", "shadow")),
        Alias("Orion", listOf("constellation", "mythic")),
        Alias("Lyra", listOf("constellation", "music")),
        Alias("Altair", listOf("star", "astronomy")),
        Alias("Sirius", listOf("star", "bright")),

        Alias("Polaris", listOf("star", "navigation")),
        Alias("Vega", listOf("star", "astronomy")),
        Alias("Rigel", listOf("star", "giant")),
        Alias("Antares", listOf("star", "red-giant")),
        Alias("Spica", listOf("star", "binary")),

        Alias("Arcturus", listOf("star", "ancient")),
        Alias("Capella", listOf("star", "binary")),
        Alias("Castor", listOf("star", "mythic")),
        Alias("Pollux", listOf("star", "mythic")),
        Alias("Deneb", listOf("star", "distant")),

        Alias("Regulus", listOf("star", "royal")),
        Alias("Fomalhaut", listOf("star", "southern")),
        Alias("Aldebaran", listOf("star", "giant")),
        Alias("Betelgeuse", listOf("star", "supergiant")),
        Alias("Procyon", listOf("star", "binary"))
    )
}

object TaglineTemplates {

    val ALL: List<(String) -> String> = listOf(

        // Calm & Reflective
        { y -> "Finding patterns in your entries since $y" },
        { y -> "Uncovering trends from your journal since $y" },
        { y -> "Connecting insights across your entries since $y" },
        { y -> "Learning from your 기록 since $y" },
        { y -> "Reading between the lines since $y" },
        { y -> "Quietly learning from your entries since $y" },
        { y -> "A quiet witness to your journey since $y" },
        { y -> "Holding space for your reflections since $y" },
        { y -> "Listening to the rhythm of your heart since $y" },
        { y -> "Your silent partner in reflection since $y" },
        { y -> "Tracing the threads of your story since $y" },
        { y -> "A mirror to your evolving self since $y" },
        { y -> "Capturing the essence of your days since $y" },

        // Witty & Funny
        { y -> "Making sense of your entries since $y" },
        { y -> "Knowing you better than your future self since $y" },
        { y -> "Overthinking your overthinking since $y" },
        { y -> "Your diary's favorite eavesdropper since $y" },
        { y -> "The only one who actually reads these since $y" },
        { y -> "Knowing exactly what you did last summer (and $y)" },
        { y -> "Decoding your cosmic chaos since $y" },
        { y -> "Keeping your secrets (mostly) since $y" },
        { y -> "Tracing patterns in your thoughts since $y" },
        { y -> "Analyzing your character arc since $y" },
        { y -> "Archiving your adventures and misadventures since $y" },
        { y -> "Your digital confidante (and occasional critic) since $y" },
        { y -> "Remembering what you forgot since $y" },

        // Playful & Cosmic
        { y -> "Stargazing through your memories since $y" },
        { y -> "Translating your late-night thoughts since $y" },
        { y -> "Sifting through your cosmic crumbs since $y" },
        { y -> "Dancing with your data since $y" },
        { y -> "Floating through your reflections since $y" },
        { y -> "Whispering to your inner universe since $y" },
        { y -> "Mapping your emotional nebula since $y" },
        { y -> "Charting your orbit through the emotional galaxy since $y" },
        { y -> "Chasing shooting stars in your journal since $y" },
        { y -> "Building a constellation out of your moods since $y" },
        { y -> "Observing how things evolve since $y" },
        { y -> "Navigating your inner labyrinth since $y" },
        { y -> "Seeing the magic in your mundane since $y" },
        { y -> "Your personal time capsule since $y" }
    )
}