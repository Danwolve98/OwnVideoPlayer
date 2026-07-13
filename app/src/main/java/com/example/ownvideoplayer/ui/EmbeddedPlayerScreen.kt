package com.example.ownvideoplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danwolve.ownvideoplayer.player.VideoPlayerViewModel
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayer
import com.danwolve.ownvideoplayer.ui.FullScreenMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedPlayerScreen(
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.playerFlow.collectAsState()

    // Cargar el video al entrar
    LaunchedEffect(Unit) {
        viewModel.loadVideo(
            "https://museusvalenciapre.grupotecopy.es/sites/default/files/2024-10/BEACON%2050%20%2B%20%20Audio%20Benlliure%2042%20%2B%20IMG.mp4".toUri()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("App Tab Example") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = "Video Content Title", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This is an example of the video player integrated within a scrollable view. You can see other UI elements around it.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Integrated Video Player
            // FullScreenMode.DIALOG handles expansion internally sharing the SAME state
            OwnVideoPlayer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                player = player,
                uiState = uiState,
                onPlayPause = { viewModel.playPause() },
                onRewind = { viewModel.rewind() },
                onFastForward = { viewModel.fastForward() },
                onSeek = { viewModel.seekTo(it) },
                onToggleFullScreen = { viewModel.toggleFullScreen() },
                onToggleControls = { viewModel.toggleControls() },
                fullScreenMode = FullScreenMode.DIALOG
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "More Details", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                style = MaterialTheme.typography.bodySmall
            )

            repeat(5) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Section $it", style = MaterialTheme.typography.titleSmall)
                Text(text = "Additional information about section $it.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
