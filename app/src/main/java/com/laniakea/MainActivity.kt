package com.laniakea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.laniakea.ui.LaniakeaApp
import com.laniakea.ui.theme.LaniakeaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable true edge-to-edge support
        enableEdgeToEdge()

        setContent {
            LaniakeaTheme {
                // 2. Main Entry Point (Now clean and slim)
                LaniakeaApp()
            }
        }
    }
}