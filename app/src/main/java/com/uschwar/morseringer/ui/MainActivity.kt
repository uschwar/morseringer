package com.uschwar.morseringer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uschwar.morseringer.ui.theme.MorseRingerTheme

/**
 * The single activity entry point of the application.
 * 
 * Sets up the edge-to-edge layout and provides the Compose [MainScreen] within 
 * the application theme.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MorseRingerTheme {
                MainScreen()
            }
        }
    }
}