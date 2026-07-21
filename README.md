# Laniakea — Private Journal Entry

## Project Status

Laniakea is currently an experimental prototype and research project.

The On-Device NLP Engine analyzes linguistic patterns in journal entries and is intended for personal reflection only.

It is not a mental health tool, diagnostic system, or clinical assessment instrument.

No claims of psychological validity are made.

> **July 2026:** Several Phase 2 insight gauges are under active reconsideration and likely redesign. See **Design Notes — Rethinking the Insight Gauges** at the end of this document.

---

## App Description
**Laniakea** is a security-focused, privacy-first personal journaling application. It is designed to be a **digital sanctuary** where users can record their thoughts while maintaining total control over their data.

Its standout feature is its **On-Device NLP Engine**, which uses **Artificial Intelligence (AI)** to analyze the semantic meaning and structural language patterns of journal entries. Unlike typical apps that rely on cloud-based inference, Laniakea performs all analysis **entirely on the device** and applies obfuscation layers (referred to as **Privacy Shields**) that raise the cost of embedding-inversion attacks if local storage is ever compromised.

Ultimately, Laniakea acts as a **personal semantic memory system** that reveals patterns in your writing over time.

### Key Capabilities
- **Journaling & Calendar:** A robust daily journaling system with intuitive calendar-based navigation.
- **Personal Semantic Memory:** Automatically connects related past entries to create a continuous narrative of thought.
- **Meaning-Based Search:** Replaces rigid keyword search with deep semantic retrieval.
- **Structural Language Trends:** Tracks how your writing evolves over time (e.g., vocabulary diversity, question frequency) instead of assigning emotional scores.
- **Period Digests & Themes:** Auto-generates insights for any selected period and clusters entries into semantic themes without manual tagging.
- **Quick Reflections:** A structured check-in system for low-energy days that seamlessly maps onto the constellation without skewing natural language analytics.

## User Privacy & Ethical Use

> **Note on App Purpose & Ethics:** Laniakea is a technical showcase of on-device AI (TensorFlow Lite / Universal Sentence Encoder) used for semantic text clustering. It is designed purely as a personal journaling and self-reflection tool. It is not a medical device, nor is it intended to diagnose, treat, or monitor any psychological or mental health conditions. All thematic clustering is based purely on mathematical vector distance (L2), not clinical psychology.

Laniakea is designed as a **personal reflection tool**, not a diagnostic or judgmental app.
- All analysis is **performed locally** on your device.
- The system observes **language patterns and structure** (e.g., vocabulary diversity, question frequency); it does **not label, diagnose, or interpret** your psychology.
- You remain in **full control** of your data and the meaning of your journal entries.
- The AI's readings are **informational**, helping you track "How has my writing changed?" rather than "How do I feel?".

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
Laniakea's core privacy guarantee comes from its architecture: on-device inference, AES-256-GCM encryption, and zero network transmission. On top of that baseline, a multi-layered **Privacy Shield** is applied to every stored embedding as defense-in-depth — obfuscation that raises the cost of embedding-inversion attacks (where original text is approximated from vectors) in the event local storage is compromised:

| Feature | Method | Purpose |
|------|------|------|
| **Laplace Noise** | Calibrated statistical noise | Perturbs stored vectors to hinder exact phrasing reconstruction |
| **Precision Clipping** | Decimal rounding | Limits information leakage by rounding values to 4 decimal places (0.0001) |
| **Vector Shuffling** | Fixed user-specific permutation | Scrambles vector indices so extracted embeddings are meaningless outside the user's device |

> **Honest framing:** These layers are obfuscation, not formal privacy guarantees. The noise injection is *inspired by* differential-privacy techniques but does not constitute formal DP (no epsilon budget is defined), and the permutation is a keyed transform rather than cryptography. They complement — not replace — the encryption and offline architecture that do the real work.

---

## 5. Phase 1: The Utility Layer (Complete)

Phase 1 establishes the core functionality of Laniakea, providing deep meaning-based search and objective structural insights into your journaling habits.

### Semantic Search & Memory
Laniakea completely replaces traditional keyword search with **meaning-based retrieval**, powered by the on-device sentence embedder.
- **Semantic Search:** Search your journal history using abstract concepts or feelings (e.g., "Moments of clarity," "Times I felt stuck") without relying on exact word matches or manual tags.
- **Semantic Memory (Find Similar):** Every journal entry features a "Find Similar" action. By calculating the L2 vector distance between entries, Laniakea resurfaces past entries where you shared the exact same state of mind, creating a continuous narrative of your thoughts over time.

### Writing Reflections (Insights)
Laniakea analyzes decrypted journal entries locally to provide structural insights into how your writing evolves over time. The **Insight Screen** lets you pick a period (7D, 14D, 1M, 3M, 6M, 1Y) and page back through previous periods, tracking 5 key writing trend metrics across whatever range is selected:
1. **Entry Length:** Tracks word count over time.
2. **Vocabulary Diversity:** Measures lexical richness (unique words vs total words).
3. **Question Frequency:** Analyzes how often you use interrogative patterns (`?`).
4. **First-Person Usage:** Tracks the density of self-referential pronouns (I, me, my). *(under review — see Design Notes)*
5. **Future vs Past Orientation:** Measures the temporal focus of your writing by comparing future-oriented keywords (will, hope, plan) against past-oriented ones (was, yesterday, remembered).

Alongside these trends, a **Cognitive Footprint** radar chart and four directional gauges (Future vs Past, Agency, Certainty, Horizon) visualize the period-level average of the cognitive metrics described in [Section 6](#cognitive-style--pacing-tracker). *(under review — see Design Notes)*

### Semantic Themes & Period Digest
- **Semantic Themes:** Using the generated embeddings, Laniakea automatically clusters your entries into recurring Semantic Themes (e.g., Relationships & Connection, Learning & Curiosity) without requiring manual tags. You can customize which themes are tracked from the Insight Screen.
- **Period Digest:** An auto-generated summary providing a structural snapshot of your journaling consistency, dominant semantic themes, and language evolution over the currently selected period (not fixed to a week - it adapts to whatever range you're viewing).

### Quick Reflection (Questionnaire) Integration
For moments when users lack the energy for a full written journal entry, Laniakea offers a structured **Quick Reflection** questionnaire (capturing Energy, Mental Pace, Social State, etc.). To maintain the integrity of the core NLP engine, these structured check-ins are handled with specific architectural nuances:
- **Synthetic Vectorization:** Explicit answers are converted into a formatted synthetic sentence (e.g., *"Today energy was drained. Primary theme was work."*) and embedded. This allows the entry to be mapped natively onto the 3D Constellation Graph alongside written entries.
- **Analytics Exclusion:** Quick Reflections are explicitly excluded from the writing trends engine. They do not artificially deflate average word counts, skew vocabulary diversity metrics, or affect syntactic pacing charts.
- **Semantic Clustering Exclusion:** They are hidden from the Semantic Themes clustering pipeline, ensuring your auto-generated thematic buckets are composed entirely of naturally written thoughts.
- **Constellation Node Scaling:** Because synthetic sentences have artificially dense semantic keywords, their base graph `importance` is mathematically constrained by a factor of 10. This prevents Questionnaire nodes from becoming visually dominant "Super Novas" on the map.
- **UI Degradation:** On the Journal Screen and Map Node detail panels, the verbose synthetic text is suppressed and cleanly reconstructed into a readable, compact summary (e.g., *"Work • Drained Energy • Racing Mind"*).

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

### Cognitive Style & Pacing Tracker *(under review — see Design Notes)*
Moving beyond basic metrics, the on-device NLP engine now extracts advanced cognitive metadata for every entry:
1. **Syntactic Pacing:** Measures thought complexity (ratio of conjunctions to sentences).
2. **Agency Score:** Tracks active "I" statements vs passive phrasing to gauge personal empowerment. *(under review)*
3. **Epistemic Modality:** Analyzes the ratio of absolute words (never, always) vs hedged words (maybe, perhaps). Surfaced in the Insight Screen as the "Certainty" gauge. *(under review)*
4. **Processing Markers:** Counts cognitive processing words (realize, understand, because) indicating causal reasoning.
5. **Temporal Horizon:** Uses vector projection onto a "Concrete vs Abstract" semantic axis to determine if an entry leans heavily concrete or abstract.

**Period-level aggregation:** Agency, Epistemic Modality, and Temporal Horizon are stored as raw (uncompressed) projections per entry and averaged directly across whatever period is selected in the Insight Screen. Earlier revisions compressed each entry with a saturating `tanh` transform before averaging, which pushed nearly every entry to the extreme ends of its range and destroyed the real variance the average was supposed to reflect. Averaging the raw values first, and only shaping the result for gauge/radar display afterward, keeps the Cognitive Footprint visualization faithful to genuine period-over-period differences in your writing.

### Mood-Text Alignment *(under review — see Design Notes)*
The engine calculates the vector position of your writing along a predefined emotional axis. It uses this to mathematically check if the subconscious tone of your text aligns with the mood you manually logged for the day.

## 7. The Accuracy Paradox: Shuffling & Noise

### Why Vector Shuffling Preserves Accuracy
- **Fixed Permutation:** Vector shuffling uses a deterministic permutation derived from a user-specific `privacySeed`, stored in encrypted settings.
- **Consistent Pipeline:** Journal entries and reference anchors are processed using the same embedding, noise, and permutation steps.
- **Permutation Invariance:** The dot product is invariant under identical reordering of dimensions. If vectors \(A\) and \(B\) are permuted using the same mapping, \(A \cdot B\) remains unchanged.

### Why Noise Does Not Break Accuracy
Laplace noise is applied using a small calibrated scale (`scale = 0.002`). In high-dimensional spaces, this level of noise preserves local neighborhood structure and does not materially affect a vector's relative position along the reference axes, while still hindering exact reconstruction. (This is also why the noise is obfuscation rather than a formal privacy guarantee — noise weak enough to preserve all neighborhood structure is, by the same token, not strong enough to defeat a determined inversion attack. The threat it addresses is casual/offline vector scraping, not a resourced adversary.)

### Result
Each user operates within a **personal vector space**. To an external observer, stored data appears as noisy, permuted numerical vectors that cannot be trivially matched to public AI models. Internally, semantic relationships remain stable, measurable, and meaningful—without exposing original text.

---

## Non-Goals
- No cloud-based analytics or inference
- No server-side storage of journal data
- No social or shared journaling features
- No psychological interpretation, scoring, or wellness claims

---

## Threat Model (High-Level)
- **Protected Against:** Data exfiltration, cloud leaks, casual embedding-inversion attempts, unauthorized local access
- **Not Protected Against:** Compromised or rooted devices, malicious OS-level attackers, physical access with valid device unlock credentials, or well-resourced inversion attacks against extracted embeddings

---

## Design Notes — Rethinking the Insight Gauges (July 2026)

> This section documents an open design problem, recorded here for transparency while the redesign is in progress.

### The problem

Laniakea's stated principle is that it observes **structure, not psychology** — "How has my writing changed?" rather than "How do I feel?". On review, several Phase 2 gauges don't fully honor that principle. The measurements themselves are structural (ratios, counts, vector projections), but the **labels and framing are interpretive**:

- **Agency Score** claims to "gauge personal empowerment" — that is a psychological interpretation of a grammatical ratio, not a description of it.
- **Mood-Text Alignment** projects writing onto a "Joy–Distress" emotional axis and implies the app can detect a *subconscious* tone the user themselves didn't report. That is exactly the kind of interpretive claim the ethics note disavows.
- **Epistemic Modality** (absolutist vs hedged words) and **First-Person Usage** (pronoun density) are, as constructs, the same linguistic markers used in clinical language-analysis research. Tracking them is structurally defensible; *presenting them as personal gauges* invites users to read them as mental-health signals — which the app explicitly is not qualified to provide and does not validate. (Note: the UI already renames Epistemic Modality to "Certainty" — a softer word, but still a trait-like label rather than a description of a word ratio.)

The current Insight Screen makes the stakes concrete: the **Cognitive Footprint radar chart** and the **four directional gauges** (Future vs Past, Agency, Certainty, Horizon) present period-level averages of these metrics as a personal readout. The recent aggregation fix — averaging raw values instead of `tanh`-saturated ones — made those averages *statistically* honest, but statistical honesty sharpens the framing problem rather than softening it: an accurate "Agency" reading averaged over a 3-month period reads even more like a personality assessment than a noisy one did. A radar chart of a person's "cognitive footprint" is, visually, a personality profile — whatever the axes are actually measuring.

In short: the disclaimers say one thing, and a handful of gauge labels quietly say another. A user watching their "Agency" gauge decline over a quarter will not experience that as a neutral syntax statistic, regardless of what the ethics note says.

### Redesign direction (tentative)

The redesign principle: **every gauge must be describable in one sentence that contains no psychological noun.** If a metric can't be named without words like *agency, empowerment, certainty, mood, distress*, either the name changes or the metric goes.

| Current | Problem | Candidate redesign |
|---|---|---|
| Agency Score ("personal empowerment") | Interprets grammar as psychology | Rename to **Active vs Passive Phrasing** — show the ratio, drop the empowerment framing entirely |
| Certainty gauge (Epistemic Modality) | Trait-like label on a clinical-adjacent construct | Rename to **Absolute vs Hedged Word Ratio**; present as a vocabulary statistic in the trends list, not a standalone gauge |
| Cognitive Footprint radar + directional gauges | Period-averaged "profile" visualization reads as a personality assessment, independent of axis names | Re-label every axis descriptively (Active/Passive, Absolute/Hedged, Concrete/Abstract, Future/Past) — and consider replacing the single radar "profile" with per-metric trend lines, which show *change* rather than *identity* |
| Mood-Text Alignment (Joy–Distress axis) | Claims access to subconscious emotional tone | Likely **remove**, or invert the direction: let the user's *own* logged mood be the ground truth and simply show nearest-entry similarity ("entries you wrote on days you logged similar moods"), with no axis and no alignment verdict |
| First-Person Usage | Pronoun density is a known clinical marker; standalone tracking invites self-diagnosis | Keep the data but demote it: fold into the general vocabulary trends view rather than a dedicated chart |

Additional guardrails under consideration:
- **No single-number scores.** Trends over time only — a score implies a rating; a trend is just a record. This applies to the directional gauges too: a period-averaged gauge is still a score.
- **No baseline judgments.** The app may show that a metric changed; it should never suggest whether the change is good, bad, or concerning.
- **Gauge-level opt-in.** The interpretive-adjacent metrics (if any survive) become individually toggleable and off by default, consistent with the app's consent-first philosophy.

### Status

Reconsidering — probably a redesign of the Insight Screen (the Cognitive Footprint radar and the four directional gauges in particular) and the Cognitive Style tracker before any public release. The semantic-search, themes, constellation, anomaly, and period-aggregation features are unaffected; the issue is confined to how a subset of metrics is *labeled and surfaced*, not to the NLP engine or its math.

---

## Summary
Laniakea is built around a single principle: **your thoughts should belong to you alone**. By combining modern Android architecture, strong cryptography, on-device machine learning, and layered obfuscation techniques, the app delivers meaningful reflection on your own writing without sacrificing user trust or control.