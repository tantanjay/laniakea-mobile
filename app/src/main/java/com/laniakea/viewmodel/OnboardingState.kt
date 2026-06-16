package com.laniakea.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class OnboardingState(
    private val onComplete: () -> Unit
) {
    var hasAgreedToTerms by mutableStateOf(false)
    var currentSlideIndex by mutableIntStateOf(0)
    
    val totalSlides = 4

    fun acceptTerms() {
        hasAgreedToTerms = true
    }

    fun nextSlide() {
        if (currentSlideIndex < totalSlides - 1) {
            currentSlideIndex++
        } else {
            finishOnboarding()
        }
    }

    fun previousSlide() {
        if (currentSlideIndex > 0) {
            currentSlideIndex--
        }
    }

    fun finishOnboarding() {
        onComplete()
    }
}
