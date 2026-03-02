# Laniakea — Private Journal Entry

## App Description
**Laniakea** is a security-focused, privacy-first personal journaling application. It is designed to be a **digital sanctuary** where users can record their thoughts while maintaining total control over their data.

Its standout feature is the **Vibe Engine**, which uses on-device **Artificial Intelligence (AI)** to analyze the emotional tone (vibe) of entries. Unlike typical apps that rely on cloud-based AI processing, Laniakea performs all analysis **entirely on-device** and applies mathematical **Privacy Shields** to ensure your reflections remain truly private.

---

## Technical Specifications

### 1. Core Architecture & UI
* **Framework:** Built entirely with **Jetpack Compose**, utilizing modern Android standards such as `ComponentActivity` and Edge-to-Edge display.
* **Pattern:** Follows the **MVVM (Model-View-ViewModel)** architecture for a clean separation of concerns between UI and business logic.
* **Theming:** Implements a custom Material 3 theme (`LaniakeaTheme`) that adapts dynamically based on the current "vibe" or user preferences.

### 2. Local Data Persistence
* **Database:** Uses the **Room Persistence Library** to manage journal entries (`Diary`) and application settings.
* **Storage Model:** All data is stored locally on the device. No cloud syncing is implemented by default, and the app functions fully offline, ensuring complete data sovereignty.

### 3. Security & Encryption
* **Key Management:** Utilizes **Jetpack Security** (`EncryptedSharedPreferences`) to securely store master encryption keys.
* **Data Encryption:** Journal content is encrypted using **AES-GCM-256** (Galois/Counter Mode), providing both confidentiality and integrity.
* **Memory Safety:** Journal content is only decrypted in memory at runtime and is never written to disk in plaintext.
* **Backup Security:** Exported backups support password-based encryption using **PBKDF2 with HmacSHA256** to derive cryptographic keys from user-provided passwords.

---

## 4. On-Device AI (NLP Engine)

Laniakea uses **TensorFlow Lite (TFLite)** to run an on-device **TensorFlow 2 `cmlm-en-base`** language model (`sentence_encoder.tflite`), which is based on the **Google Universal Sentence Encoder (USE)** architecture.

The model generates **768-dimensional semantic embeddings** from journal text, enabling local NLP features such as semantic understanding and similarity analysis **without sending data off-device**.

To prevent *inversion attacks*—where original text is reconstructed from embeddings—a multi-layered **Privacy Shield** is applied to every vector:

| Feature | Method | Purpose |
|------|------|------|
| **Laplace Noise** | Differential Privacy | Adds calibrated statistical noise to prevent exact phrasing reconstruction. |
| **Precision Clipping** | Decimal Rounding | Limits information leakage by rounding values to 3 decimal places. |
| **Vector Shuffling** | Encrypted User Seed | Randomizes vector indices so stolen vectors are mathematically meaningless. |

---

## 5. The Vibe System & Privacy Math

Conceptually, the **Vibe Score** measures whether a journal entry lies closer to a *Joy* reference or a *Distress* reference in semantic embedding space.

The **VibeEngine** computes this score by projecting an entry embedding \(E\) onto an axis defined by two reference anchors—Joy (\(A_{joy}\)) and Distress (\(A_{distress}\)):

\[
\text{Vibe Score} =
\frac{E \cdot (A_{joy} - A_{distress})}
{\|A_{joy} - A_{distress}\|}
\]

### The Accuracy Paradox: Shuffling & Noise

A common question is how the Vibe Score remains accurate when vectors are intentionally corrupted for privacy.

#### Why Shuffling Does Not Break Accuracy
1. **Symmetry:** Vector shuffling uses a fixed permutation map derived from a unique `privacySeed` stored in encrypted user settings.
2. **Consistent Scrambling:** Reference anchors (Joy/Distress) are generated using the same embedding pipeline and shuffled using the exact same permutation.
3. **Permutation Invariance:** The dot product is permutation invariant. If vectors \(A\) and \(B\) are reordered using the same mapping, \(A \cdot B\) remains unchanged.

#### Why Noise Does Not Break Accuracy
Laplace noise is applied with a small calibrated scale (`scale = 0.01`), preserving the local neighborhood structure of vectors in high-dimensional space. While this prevents exact reconstruction of text, it does not significantly affect the vector’s relative position along the Joy–Distress axis.

### Result
Laniakea creates a **Personal Vector Space** unique to each user. To an external observer, the data appears as scrambled, noisy numerical vectors that cannot be matched to any public AI model. Internally, however, the semantic relationships between a user’s thoughts remain stable, measurable, and meaningful—without ever exposing the original text.

---

## Non-Goals
* No cloud-based analytics or inference
* No server-side storage of journal data
* No social or shared journaling features

---

## Threat Model (High-Level)
* **Protected Against:** Data exfiltration, cloud leaks, vector inversion attacks, unauthorized local access.
* **Not Protected Against:** Compromised or rooted devices, malicious OS-level attackers, physical access with device unlock credentials.

---

## Summary
Laniakea is built around a single principle: **your thoughts should belong to you alone**. By combining modern Android architecture, strong encryption, on-device AI, and mathematically grounded privacy techniques, the app delivers meaningful insights without sacrificing trust or control.