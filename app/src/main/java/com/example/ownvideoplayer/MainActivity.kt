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
import com.example.ownvideoplayer.ui.MenuScreen
import com.example.ownvideoplayer.ui.EmbeddedPlayerScreen
import com.example.ownvideoplayer.ui.VideoPlayerScreen
import com.example.ownvideoplayer.ui.theme.OwnVideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OwnVideoPlayerTheme {
                val backStack = remember { mutableStateListOf<Any>(Route.Menu) }
                
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) },
                    entryProvider = { key ->
                        when (key) {
                            is Route.Menu -> NavEntry(key) {
                                MenuScreen(
                                    onNavigateToEmbedded = { backStack.add(Route.EmbeddedPlayer) },
                                    onNavigateToFullScreen = { backStack.add(Route.FullScreenPlayer) }
                                )
                            }
                            is Route.EmbeddedPlayer -> NavEntry(key) { 
                                EmbeddedPlayerScreen()
                            }
                            is Route.FullScreenPlayer -> NavEntry(key) { 
                                VideoPlayerScreen(
                                    onDismiss = { if (backStack.last() is Route.FullScreenPlayer) backStack.removeAt(backStack.size - 1) }
                                ) 
                            }
                            else -> error("Unknown route: $key")
                        }
                    }
                )
            }
        }
    }
}
