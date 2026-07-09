package com.danwolve.ownvideoplayer.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.PlayerSurface
import com.danwolve.ownvideoplayer.player.VideoPlayerUiState
import com.danwolve.ownvideoplayer.player.VideoQuality
import java.util.Locale

@Composable
fun OwnVideoPlayer(
    modifier: Modifier = Modifier,
    videoModifier: Modifier = Modifier,
    player: androidx.media3.common.Player?,
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleControls: () -> Unit,
    onQualityClick: (VideoQuality) -> Unit,
    isControlsEnabled: Boolean = true
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (isControlsEnabled) Modifier.clickable(onClick = onToggleControls)
                else Modifier
            )
    ) {
        // Video Layer: Constrained by videoModifier (aspect ratio) but centered in the full-size Box
        Box(
            modifier = videoModifier.align(Alignment.Center)
        ) {
            player?.let { PlayerSurface(player = it) }
        }

        if (uiState.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Red
            )
        }

        // UI Layer: Always occupies the entire modifier (usually fillMaxSize)
        if (isControlsEnabled) {
            AnimatedVisibility(
                visible = uiState.isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                YouTubeControlsOverlay(
                    uiState = uiState,
                    onPlayPause = onPlayPause,
                    onRewind = onRewind,
                    onFastForward = onFastForward,
                    onSeek = onSeek,
                    onToggleFullScreen = onToggleFullScreen,
                    onQualityClick = onQualityClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeControlsOverlay(
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onQualityClick: (VideoQuality) -> Unit
) {
    var draggingPosition by remember { mutableStateOf<Float?>(null) }
    val sliderValue = draggingPosition ?: uiState.currentPosition.toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        // Top controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            var showQualityDialog by remember { mutableStateOf(false) }
            IconButton(onClick = { showQualityDialog = true }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
            }
            if (showQualityDialog) {
                QualitySelectionDialog(
                    qualities = uiState.availableQualities,
                    selectedQuality = uiState.selectedQuality,
                    onQualitySelected = {
                        onQualityClick(it)
                        showQualityDialog = false
                    },
                    onDismiss = { showQualityDialog = false }
                )
            }
        }

        // Center controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            IconButton(onClick = onRewind, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Replay10, contentDescription = "Rewind", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = onFastForward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Forward10, contentDescription = "Forward", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTime(sliderValue.toLong())} / ${formatTime(uiState.duration)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        imageVector = if (uiState.isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = Color.White
                    )
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { draggingPosition = it },
                onValueChangeFinished = {
                    draggingPosition?.let {
                        onSeek(it.toLong())
                        draggingPosition = null
                    }
                },
                valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Red,
                    activeTrackColor = Color.Red,
                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                ),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(2.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        )
                    )
                },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        modifier = Modifier.size(12.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.Red)
                    )
                }
            )
        }
    }
}

@Composable
fun QualitySelectionDialog(
    qualities: List<VideoQuality>,
    selectedQuality: VideoQuality?,
    onQualitySelected: (VideoQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quality Settings") },
        text = {
            Column {
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (quality == selectedQuality),
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = quality.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PermissionDeniedPlaceholder(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Permission required", color = Color.White)
        Button(onClick = onGrant) { Text("Grant Permission") }
    }
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
