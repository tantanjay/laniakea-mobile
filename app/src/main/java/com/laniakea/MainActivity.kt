package com.laniakea

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laniakea.ui.LaniakeaApp
import com.laniakea.ui.theme.LaniakeaTheme
import com.laniakea.viewmodel.LaniakeaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Enable true edge-to-edge support
        enableEdgeToEdge()

        setContent {
            val vm: LaniakeaViewModel = viewModel()
            
            DisposableEffect(Unit) {
                val listener = Consumer<Intent> { newIntent ->
                    if (newIntent.getBooleanExtra("OPEN_QUICK_REFLECTION", false)) {
                        vm.pendingQuickReflection = true
                        newIntent.removeExtra("OPEN_QUICK_REFLECTION")
                    }
                }
                addOnNewIntentListener(listener)
                
                // Also check initial intent
                if (intent.getBooleanExtra("OPEN_QUICK_REFLECTION", false)) {
                    vm.pendingQuickReflection = true
                    intent.removeExtra("OPEN_QUICK_REFLECTION")
                }
                
                onDispose {
                    removeOnNewIntentListener(listener)
                }
            }

            LaniakeaTheme(theme = vm.theme) {
                // 2. Main Entry Point (Now clean and slim)
                LaniakeaApp(vm)
            }
        }
    }
}