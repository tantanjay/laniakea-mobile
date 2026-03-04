# Laniakea — Private Journal Entry

## App Description
**Laniakea** is a security-focused, privacy-first personal journaling application. It is designed to be a **digital sanctuary** where users can record their thoughts while maintaining total control over their data.

Its standout feature is the **Vibe Engine**, which uses fully on-device **Artificial Intelligence (AI)** to analyze the emotional tone (“vibe”) of journal entries. Unlike typical apps that rely on cloud-based inference, Laniakea performs all analysis **entirely on the device** and applies mathematically grounded, privacy-preserving transformations (referred to as **Privacy Shields**) to ensure that reflections remain truly private.

---

## Technical Specifications

## 1. Core Architecture & UI
- **UI Framework:** The user interface is built entirely with **Jetpack Compose**, following modern Android standards such as `ComponentActivity` and Edge-to-Edge display.
- **Architecture Pattern:** Implements **MVVM (Model–View–ViewModel)** to enforce a clean separation between UI, state, and business logic.
- **Theming:** Uses a custom Material 3 theme (`LaniakeaTheme`) that adapts dynamically based on the current Vibe or explicit user preferences.

---

## 2. Local Data Persistence
- **Database:** Journal entries (`Diary`) and application settings are stored using the **Room Persistence Library**.
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

## 5. The Vibe System & Privacy Math

Laniakea supports **multiple entries per day**. Each entry generates its own embedding, and the Vibe Engine updates the personalized baseline dynamically, reflecting changes in mood even within a single day.

The **Vibe Score** measures whether a journal entry lies closer to a *Joy* or *Distress* reference point within semantic embedding space.

### Emotional Baseline
The system initializes an emotional axis using two fixed anchor sentences:

- **Joy Anchor:** *“I feel incredibly happy, fulfilled, and optimistic.”*
- **Distress Anchor:** *“I feel miserable, exhausted, and hopeless.”*

Given an entry embedding \(E\), the Vibe Score is computed by projecting it onto the Joy–Distress axis:

\[
\text{Vibe Score} =
\frac{E \cdot (A_{joy} - A_{distress})}
{\|A_{joy} - A_{distress}\|}
\]

---

## 6. Dynamic Calibration (20-Sentiment Model)

While Laniakea starts with generic emotional anchors, the system adapts over time using **Dynamic Calibration**.

1. **Personalized Learning:** After at least five entries are explicitly marked as extreme moods (Joy or Miserable), the engine begins averaging embeddings from the **most recent 20 sentiments** in each category.
2. **Adaptive Baseline:** These rolling averages replace the initial anchors, aligning the emotional axis with the user’s unique language and expression patterns.
3. **Why 20 Is the Sweet Spot:**
    - **Responsiveness:** The baseline adapts to changes in life circumstances within weeks.
    - **Recency Bias:** Users experience feedback that reflects their current emotional state.
    - **Performance:** Averaging 20 vectors of 768 dimensions is computationally trivial on modern devices.
4. **Why It Matters:** Emotional expression is subjective. By calibrating against personal history rather than a generic lexicon, Laniakea creates a **personalized emotional compass** unique to each user.

---

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