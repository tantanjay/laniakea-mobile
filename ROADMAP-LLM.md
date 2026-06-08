# Laniakea Roadmap: Local LLM Integration

This roadmap outlines the theoretical architecture and bridge logic required to upgrade Laniakea to utilize **local, on-device LLMs** (Large Language Models) like Gemini Nano via Android AICore or MLC-LLM. 

By leveraging an on-device LLM, Laniakea can remain 100% private and offline while unlocking powerful generative capabilities that surpass current heuristic and basic embedding logic.

## 1. The Bridge Logic Architecture

To ensure Laniakea continues to work flawlessly on older or less capable devices, the LLM integration must act as a seamless "Bridge" or "Progressive Enhancement."

### Core Interface: `LlmBridge`
Create an abstraction layer that handles the capability checks and generation requests.

```kotlin
interface LlmBridge {
    suspend fun isAvailable(): Boolean
    suspend fun generateCompletion(prompt: String, context: List<DiaryEntry>): Flow<String>
}
```

### Progressive Enrichment (Not a Replacement)
The `SentenceEmbedder` remains the **core backbone** of Laniakea on *all* devices. It will always handle the fundamental tasks: generating vectors, clustering themes, and powering semantic search and retrieval.

When the app launches, `LaniakeaViewModel` will check for LLM support:
- **If LLM is available:** Enable an "enrichment" layer. The LLM sits on top of the `SentenceEmbedder` to summarize clusters, chat about retrieved entries, or name dynamic themes.
- **If LLM is NOT available:** The app continues to function flawlessly using the core `SentenceEmbedder` and the standard `SemanticManager` centroids, ensuring no loss of base functionality.

## 2. Technical Implementation Steps

### Step 1: Android AICore Integration
For modern Android devices (Android 14+), Google provides **AICore** to access Gemini Nano locally.
*   **Dependency:** Add Google's AICore SDK to `build.gradle.kts`.
*   **Initialization:** Request download of the Nano model weights during app onboarding.
*   **Safety:** Ensure the privacy policy explicitly states the model runs completely on-device.

### Step 2: The RAG (Retrieval-Augmented Generation) Pipeline
Since Laniakea already has a blazing fast local vector database (ObjectBox HNSW) and `SentenceEmbedder`, we already have the hardest part of RAG solved!
1. User asks a question or requests an insight.
2. `SentenceEmbedder` converts the query to a vector.
3. ObjectBox retrieves the Top-K most relevant `DiaryEntry` objects.
4. `LlmBridge` takes the Top-K entries, injects them into a system prompt, and asks the local LLM to generate an answer.

## 3. Future Feature Capabilities

Once the bridge is active, Laniakea can unlock these advanced features:

### Dynamic Theme Generation
Instead of using 12 hardcoded generic themes, the LLM can read the week's entries and invent highly specific, personalized themes (e.g., "Anxiety about the upcoming move" or "Reconnecting with nature").

### Conversational Journaling
A chat interface where you can "talk" to your past self. 
*Prompt:* "Have I been feeling more anxious lately?"
*LLM Output:* "Based on your entries from Tuesday and Thursday, you've mentioned tapping your fingers and feeling overwhelmed by deadlines. However, you also noted..."

### Advanced Weekly Digests
Replace the rule-based bullet points in the `WeeklyDigestManager` with a beautifully written, empathetic prose summary of the user's emotional arc over the past 7 days.

## 4. Hardware Constraints & Considerations
*   **Battery & Thermal:** Generating LLM tokens is computationally expensive. Insights should likely be generated asynchronously or during charging (similar to how `LaniakeaViewModel.processMissingEntries` works now).
*   **RAM Limits:** On-device LLMs typically require 1-2GB of dedicated RAM. The `isAvailable()` check must strictly verify memory limits to avoid crashing older phones.
