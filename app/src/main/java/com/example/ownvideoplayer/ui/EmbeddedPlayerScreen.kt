package com.example.ownvideoplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayer
import com.danwolve.ownvideoplayer.ui.VideoSource
import com.danwolve.ownvideoplayer.ui.FullScreenMode
import androidx.compose.ui.tooling.preview.Preview
import com.danwolve.ownvideoplayer.player.VideoPlayerUiState
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayerBase
import com.example.ownvideoplayer.ui.theme.OwnVideoPlayerTheme

@Composable
fun EmbeddedPlayerScreen() {
    EmbeddedPlayerContent()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedPlayerContent() {
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

            // Reproductor integrado
            OwnVideoPlayer(
                source = VideoSource.Url("YOUR_URL"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun EmbeddedPlayerScreenPreview() {
    OwnVideoPlayerTheme {
        // Usamos OwnVideoPlayerBase para la preview ya que permite pasar un estado mock
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("App Tab Example Preview") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(text = "Video Content Title", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                OwnVideoPlayerBase(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    player = null,
                    uiState = VideoPlayerUiState(
                        isControlsVisible = true,
                        duration = 120000L,
                        currentPosition = 45000L,
                        playWhenReady = true
                    ),
                    onPlayPause = {},
                    onRewind = {},
                    onFastForward = {},
                    onSeek = {},
                    onToggleFullScreen = {},
                    onToggleControls = {}
                )
            }
        }
    }
}
