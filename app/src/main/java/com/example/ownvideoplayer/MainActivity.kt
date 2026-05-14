package com.example.ownvideoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.example.ownvideoplayer.navigation.Route
import com.example.ownvideoplayer.ui.VideoPlayerScreen
import com.example.ownvideoplayer.ui.theme.OwnVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwnVideoPlayerTheme {
                val backStack = remember { mutableStateListOf<Any>(Route.Player) }
                
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    entryProvider = { key ->
                        when (key) {
                            is Route.Player -> NavEntry(key) { VideoPlayerScreen() }
                            is Route.FullScreenPlayer -> NavEntry(key) { VideoPlayerScreen() }
                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
        }
    }
}
