package com.laniakea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laniakea.viewmodel.OnboardingState

@Composable
fun OnboardingScreen(state: OnboardingState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        if (!state.hasAgreedToTerms) {
            TermsAndPrivacyScreen(state)
        } else {
            OnboardingSlidesScreen(state)
        }
    }
}

@Composable
fun TermsAndPrivacyScreen(state: OnboardingState) {
    var isChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
            Spacer(modifier = Modifier.height(48.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Privacy Shield",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("Terms of Service & Privacy Policy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            val terms = listOf(
                "1. OVERVIEW" to "Laniakea is a privacy-first, local-only application. Your journal entries, semantic data, and personal details remain stored locally on your device; we do not maintain external servers.\nBy downloading, installing, or using Laniakea (\"the App\"), you agree to these Terms of Service and Privacy Policy.",
                "2. INFORMATION WE COLLECT & HOW WE USE IT" to "• Biometric Data: We may use device biometrics for secure access. Your biometric data never leaves your device.\n• User Content & Data Transit: Journal entries and metadata are stored entirely locally. Sensitive values are encrypted (AES-256).\n• AI Features & Processing: All AI models (Semantic Engine, Cognitive Tracker) run entirely on-device. No text is ever sent to external APIs or third-party servers.",
                "3. THIRD-PARTY SERVICES" to "• ObjectBox Vector Database: Local storage for semantic vectors.\n• TensorFlow Lite: Local AI model execution.\nNo user data is transmitted to these third parties.",
                "4. FREEWARE" to "• Free to Use: The app is free and operates entirely independently without requiring API keys or subscriptions.",
                "5. SECURITY & DATA RETENTION" to "• No Backup Liability: All data is local. Losing the device or deleting the app may result in permanent data loss. You are responsible for backups.\n• No Password Recovery: We cannot reset passwords or recover encrypted databases.\n• Data Deletion: Clear cache/data or uninstall the app to remove all data permanently.",
                "6. CHILDREN'S PRIVACY" to "We do not collect data from anyone under 13. Parents may remove data by clearing app data or uninstalling.",
                "7. DISCLAIMER OF WARRANTIES" to "• \"AS IS\": The app is provided without warranties of any kind.\n• Experimental Tool: Features like Vibe Analysis, Constellation Map, and Cognitive Tracking are experimental. They are not clinical, psychological, or diagnostic tools. You are responsible for your own reflections.",
                "8. LIMITATION OF LIABILITY" to "We are not liable for damages, data loss, or emotional distress related to app use to the fullest extent allowed by law.",
                "9. INDEMNIFICATION" to "You agree to hold the developer harmless from claims, liabilities, or costs arising from your use of the app.",
                "10. GOVERNING LAW" to "These Terms are governed by the Republic of the Philippines. Courts in the Philippines have exclusive jurisdiction. Users exercise their data rights directly via the app.",
                "11. SEVERABILITY" to "If any provision is invalid, the remaining Terms remain in effect.",
                "12. CHANGES TO THIS AGREEMENT" to "Terms may change; users should review them periodically. Updates are posted in the app.",
                "13. DEVICE PERMISSIONS" to "• Storage: To save encrypted local data.\n• Service Permissions: Standard system access for internal operations.",
                "14. CONTACT US" to "Questions? Email: cjs.dev.studio@gmail.com"
            )

            terms.forEach { (heading, body) ->
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it }
                )
                Text("I understand and agree to the Privacy Policy and Terms of Use.", style = MaterialTheme.typography.bodyMedium)
            }
            
            Button(
                onClick = { state.acceptTerms() },
                enabled = isChecked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Accept & Continue")
            }
        }
    }
}

data class SlideInfo(val title: String, val description: String, val icon: ImageVector)

val slides = listOf(
    SlideInfo(
        "Personal Semantic Memory",
        "Laniakea automatically connects related past entries to create a continuous narrative of thought. Find similar moments in your history based on deep meaning, not just keywords.",
        Icons.Default.Search
    ),
    SlideInfo(
        "The Constellation Map",
        "Explore your thoughts in a 3D semantic galaxy. Watch how your reflections cluster into semantic themes over time.",
        Icons.Default.Polyline
    ),
    SlideInfo(
        "Cognitive Tracking",
        "Track how your writing evolves over time. Laniakea analyzes structural patterns like vocabulary diversity, epistemic modality, and temporal focus.",
        Icons.Default.Timeline
    ),
    SlideInfo(
        "A Critical Clarification",
        "Laniakea analyzes language and statistical structure, not lived experience. Emotion and thought are not being reduced to numbers. It is a tool for reflection, not a measure of your soul.",
        Icons.Default.Warning
    )
)

@Composable
fun OnboardingSlidesScreen(state: OnboardingState) {
    val slide = slides[state.currentSlideIndex]
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = slide.icon,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = slide.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in slides.indices) {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (i == state.currentSlideIndex) 10.dp else 6.dp)
                            .background(
                                color = if (i == state.currentSlideIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.currentSlideIndex > 0) {
                    TextButton(onClick = { state.previousSlide() }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }
                
                Button(onClick = { state.nextSlide() }) {
                    Text(if (state.currentSlideIndex == slides.size - 1) "Get Started" else "Next")
                }
            }
        }
    }
}
