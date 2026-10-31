package com.optork.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = ACC,
                    onPrimary = Color.Black,
                    background = BG,
                    surface = SURF,
                    onSurface = Color.White,
                    onBackground = Color.White
                )
            ) {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Optimizer.restoreIfActive(this)
    }
}
