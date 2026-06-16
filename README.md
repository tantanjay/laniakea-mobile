# Laniakea — Private Journal Entry

## Project Status

Laniakea is currently an experimental prototype and research project.

The On-Device NLP Engine analyzes linguistic patterns in journal entries and is intended for personal reflection only.

It is not a mental health tool, diagnostic system, or clinical assessment instrument.

No claims of psychological validity are made.

---

## App Description
**Laniakea** is a security-focused, privacy-first personal journaling application. It is designed to be a **digital sanctuary** where users can record their thoughts while maintaining total control over their data.

Its standout feature is its **On-Device NLP Engine**, which uses **Artificial Intelligence (AI)** to analyze the semantic meaning and structural language patterns of journal entries. Unlike typical apps that rely on cloud-based inference, Laniakea performs all analysis **entirely on the device** and applies mathematically grounded, privacy-preserving transformations (referred to as **Privacy Shields**) to ensure that reflections remain truly private.

Ultimately, Laniakea acts as a **personal semantic memory system** that reveals patterns in your writing over time.

### Key Capabilities
- **Journaling & Calendar:** A robust daily journaling system with intuitive calendar-based navigation.
- **Personal Semantic Memory:** Automatically connects related past entries to create a continuous narrative of thought.
- **Meaning-Based Search:** Replaces rigid keyword search with deep semantic retrieval.
- **Structural Language Trends:** Tracks how your writing evolves over time (e.g., vocabulary diversity, question frequency) instead of assigning emotional scores.
- **Weekly Digests & Themes:** Auto-generates weekly insights and clusters entries into semantic themes without manual tagging.

## User Privacy & Ethical Use

> **Note on App Purpose & Ethics:** Laniakea is a technical showcase of on-device AI (TensorFlow Lite / Universal Sentence Encoder) used for semantic text clustering. It is designed purely as a personal journaling and self-reflection tool. It is not a medical device, nor is it intended to diagnose, treat, or monitor any psychological or mental health conditions. All thematic clustering is based purely on mathematical vector distance (L2), not clinical psychology.

Laniakea is designed as a **personal reflection tool**, not a diagnostic or judgmental app.
- All analysis is **performed locally** on your device.
- The system observes **language patterns and structure** (e.g., vocabulary diversity, question frequency); it does **not label, diagnose, or interpret** your psychology.
- You remain in **full control** of your data and the meaning of your journal entries.
- The AI’s readings are **informational**, helping you track "How has my writing changed?" rather than "How do I feel?".

---

## Technical Specifications

## 1. Core Architecture & UI
- **UI Framework:** The user interface is built entirely with **Jetpack Compose**, following modern Android standards such as `ComponentActivity` and Edge-to-Edge display.
- **Architecture Pattern:** Implements **MVVM (Model–View–ViewModel)** to enforce a clean separation between UI, state, and business logic.
- **Theming:** Uses a custom Material 3 theme (`LaniakeaTheme`).

---

## 2. Local Data Persistence
- **Relational Database:** Journal entries (`Diary`) and application settings are stored using the **Room Persistence Library**.
- **Vector Database:** High-dimensional semantic embeddings (768-D) are stored and indexed using **ObjectBox** with HNSW (Hierarchical Navigable Small World) indexing. This enables lightning-fast semantic search queries directly on the device.
- **Storage Model:** All data remains local to the device. The app functions fully offline and includes no cloud syncing or analytics SDKs by default, ensuring complete data sovereignty.

---

## 3. Security & Encryption
- **Key Management:** Master encryption keys are stored using **Jetpack Security** (`EncryptedSharedPreferences`).
- **Data Encryption:** Journal content is protected using **AES-256 in GCM mode**, providing both confidentiality and authenticated integrity.
- **Memory Safety:** Journal content is decrypted only in memory at runtime and is never intentionally written to disk in plaintext.
- **Backup Security:** Exported backups support password-based encryption. Cryptographic keys are derived from user-provided passwords using **PBKDF2 with HmacSHA256**.

---

## 4. On-Device AI (NLP Engine)

Laniakea uses **TensorFlow Lite (TFLite)** to run an on-device sentence encoder (`sentence_encoder.tflite`), based on a **TensorFlow 2–compatible `cmlm-en-base` model** derived from the **Universal Sentence Encoder (USE)** architecture.

The model produces **768-dimensional semantic embeddings** and supports sequences of up to **256 tokens** (approximately 180–200 words), enabling meaningful analysis of long-form journal entries **without transmitting data off-device**.

### Privacy Shields
To reduce the risk of embedding inversion attacks—where original text is reconstructed from vectors—a multi-layered Privacy Shield is applied to every embedding:

| Feature | Method | Purpose |
|------|------|------|
| **Laplace Noise** | Differential privacy | Adds calibrated statistical noise to prevent exact phrasing reconstruction |
| **Precision Clipping** | Decimal rounding | Limits information leakage by rounding values to 4 decimal places (0.0001) |
| **Vector Shuffling** | Fixed user-specific permutation | Scrambles vector indices so extracted embeddings are meaningless outside the user’s device |

---

## 5. Phase 1: The Utility Layer (Complete)

Phase 1 establishes the core functionality of Laniakea, providing deep meaning-based search and objective structural insights into your journaling habits.

### Semantic Search & Memory
Laniakea completely replaces traditional keyword search with **meaning-based retrieval**, powered by the on-device sentence embedder.
- **Semantic Search:** Search your journal history using abstract concepts or feelings (e.g., "Moments of clarity," "Times I felt stuck") without relying on exact word matches or manual tags.
- **Semantic Memory (Find Similar):** Every journal entry features a "Find Similar" action. By calculating the L2 vector distance between entries, Laniakea resurfaces past entries where you shared the exact same state of mind, creating a continuous narrative of your thoughts over time.

### Writing Reflections (Insights)
Laniakea analyzes decrypted journal entries locally to provide structural insights into how your writing evolves over time. The **Insight Screen** tracks 5 key metrics over your last 30 entries:
1. **Entry Length:** Tracks word count over time.
2. **Vocabulary Diversity:** Measures lexical richness (unique words vs total words).
3. **Question Frequency:** Analyzes how often you use interrogative patterns (`?`).
4. **First-Person Usage:** Tracks the density of self-referential pronouns (I, me, my).
5. **Future vs Past Orientation:** Measures the temporal focus of your writing by comparing future-oriented keywords (will, hope, plan) against past-oriented ones (was, yesterday, remembered).

### Semantic Themes & Weekly Digest
- **Semantic Themes:** Using the generated embeddings, Laniakea automatically clusters your entries into recurring Semantic Themes (e.g., Relationships & Connection, Learning & Curiosity) without requiring manual tags.
- **Weekly Reflection Digest:** An auto-generated weekly summary providing a structural snapshot of your journaling consistency, dominant semantic themes, and language evolution over the past week.

---

## 6. Phase 2: The Structure Layer (Complete)

Phase 2 introduces powerful structural awareness to the journal, turning text into an interconnected and mathematically mapped cognitive space.

### The Constellation Map (Entry Graph)
Laniakea features a fully interactive **3D Semantic Visualization Map**, rendering your journal entries as a vast, interconnected galaxy of thoughts.
- **Triple Layout Engines (Clusters, Galaxy & Time Warp):** Switch seamlessly between semantic *Clusters* (grouping nodes by semantic communities into stable spatial clusters), the *Galaxy* (where nodes form a sprawling radial spiral with arms based on theme and time), and *Time Warp* (where the entire map is physically pulled into a linear chronological tunnel, sorting thoughts into twisted semantic "lanes" over time).
- **Advanced Touch Controls:** Fluidly navigate the 3D space using 1-finger drag for camera rotation, 2-finger pinch for zooming, and 3-finger drag for lateral canvas panning.
- **Force-Directed Physics Engine:** A custom-built engine simulates gravity and spring forces, organically pulling semantically similar thoughts together to form topical clusters in 3D space.
- **Focus Mode:** Select any specific node to isolate it. The map dims the background noise and illuminates only its direct connections, complete with dynamically scaled info cards showing themes, dates, and mood labels.
- **Deterministic Formations:** Uses mathematically seeded algorithms to ensure your constellation remains stable and familiar every time you load the map, rather than shuffling randomly.
- **High-Performance Rendering:** Custom Jetpack Compose Canvas implementation featuring dynamic zoom scaling, exponential depth culling, and pagination (loading the most recent 1000 entries at a time) to maintain a smooth 60FPS experience.

### Journal Replay
An animated chronological replay that redraws your constellation node-by-node from your very first entry to your latest, allowing you to visually watch how your semantic clusters formed over time.

### Anomaly & Novelty Detection
The engine mathematically tracks when a new thought drastically deviates from your established baseline. Using a specialized `AnomalyDetector`, it calculates the L2 distance between a new entry and the centroid of your last 30 entries. Entries exceeding the orthogonal threshold (1.4f) are structurally flagged as completely novel thoughts.

### Cognitive Style & Pacing Tracker
Moving beyond basic metrics, the on-device NLP engine now extracts advanced cognitive metadata for every entry:
1. **Syntactic Pacing:** Measures thought complexity (ratio of conjunctions to sentences).
2. **Agency Score:** Tracks active "I" statements vs passive phrasing to gauge personal empowerment.
3. **Epistemic Modality:** Analyzes the ratio of absolute words (never, always) vs hedged words (maybe, perhaps).
4. **Processing Markers:** Counts cognitive processing words (realize, understand, because) indicating causal reasoning.
5. **Temporal Horizon:** Uses vector projection to determine if an entry leans heavily Concrete or Abstract.

### Mood-Text Alignment
The engine calculates the vector position of your writing along a predefined emotional axis. It uses this to mathematically check if the subconscious tone of your text aligns with the mood you manually logged for the day.

## 7. The Accuracy Paradox: Shuffling & Noise

### Why Vector Shuffling Preserves Accuracy
- **Fixed Permutation:** Vector shuffling uses a deterministic permutation derived from a user-specific `privacySeed`, stored in encrypted settings.
- **Consistent Pipeline:** Journal entries and reference anchors are processed using the same embedding, noise, and permutation steps.
- **Permutation Invariance:** The dot product is invariant under identical reordering of dimensions. If vectors \(A\) and \(B\) are permuted using the same mapping, \(A \cdot B\) remains unchanged.

### Why Noise Does Not Break Accuracy
Laplace noise is applied using a small calibrated scale (`scale = 0.002`). In high-dimensional spaces, this level of noise preserves local neighborhood structure and does not materially affect a vector’s relative position along the Joy–Distress axis, while still preventing exact reconstruction.

### Result
Each user operates within a **personal vector space**. To an external observer, stored data appears as noisy, permuted numerical vectors that cannot be matched to public AI models. Internally, semantic relationships remain stable, measurable, and meaningful—without exposing original text.

---

## Non-Goals
- No cloud-based analytics or inference
- No server-side storage of journal data
- No social or shared journaling features

---

## Threat Model (High-Level)
- **Protected Against:** Data exfiltration, cloud leaks, embedding inversion attacks, unauthorized local access
- **Not Protected Against:** Compromised or rooted devices, malicious OS-level attackers, or physical access with valid device unlock credentials

---

## Summary
Laniakea is built around a single principle: **your thoughts should belong to you alone**. By combining modern Android architecture, strong cryptography, on-device machine learning, and mathematically sound privacy techniques, the app delivers meaningful emotional insight without sacrificing user trust or control.
