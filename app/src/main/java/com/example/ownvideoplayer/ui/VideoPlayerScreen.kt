package com.example.ownvideoplayer.ui

import android.Manifest
import android.os.Build
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
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.compose.PlayerSurface
import com.example.ownvideoplayer.player.VideoPlayerViewModel
import com.example.ownvideoplayer.player.VideoQuality
import com.example.ownvideoplayer.player.VideoPlayerUiState
import com.example.ownvideoplayer.player.NotificationInfo
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = viewModel(),
    isLooping: Boolean = false,
    isControlsEnabled: Boolean = true,
    notificationInfo: NotificationInfo? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val player = viewModel.player
    val configuration = LocalConfiguration.current
    val view = LocalView.current

    // Permissions handling
    val permissionsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_VIDEO)
            if (notificationInfo != null) {
                list.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        list
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(permissionsToRequest)

    // Sync repeat mode
    LaunchedEffect(isLooping, player) {
        viewModel.setRepeatMode(isLooping)
    }

    // Sync full screen with orientation
    LaunchedEffect(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewModel.setFullScreen(true)
        } else {
            viewModel.setFullScreen(false)
        }
    }

    // Handle System UI (Status/Navigation bars)
    LaunchedEffect(uiState.isFullScreen) {
        val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (uiState.isFullScreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(Unit) {
        if (!multiplePermissionsState.allPermissionsGranted) {
            multiplePermissionsState.launchMultiplePermissionRequest()
        }
        // Example: load a video on start
        viewModel.loadVideo(
            "https://museusvalenciapre.grupotecopy.es/sites/default/files/2024-10/BEACON%2050%20%2B%20%20Audio%20Benlliure%2042%20%2B%20IMG.mp4".toUri(),
            notificationInfo
        )
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (multiplePermissionsState.allPermissionsGranted) {
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                
                // The Video Scaling Modifier
                val videoModifier = if (isLandscape) {
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(uiState.videoAspectRatio, matchHeightConstraintsFirst = true)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(uiState.videoAspectRatio, matchHeightConstraintsFirst = false)
                }

                OwnVideoPlayer(
                    player = player,
                    uiState = uiState,
                    onPlayPause = { viewModel.playPause() },
                    onRewind = { viewModel.rewind() },
                    onFastForward = { viewModel.fastForward() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleFullScreen = { viewModel.toggleFullScreen() },
                    onToggleControls = { viewModel.toggleControls() },
                    onQualityClick = { viewModel.setQuality(it) },
                    modifier = Modifier.fillMaxSize(),
                    videoModifier = videoModifier,
                    isControlsEnabled = isControlsEnabled
                )
            } else {
                PermissionDeniedPlaceholder { multiplePermissionsState.launchMultiplePermissionRequest() }
            }
        }
    }
}

@Composable
fun OwnVideoPlayer(
    player: androidx.media3.common.Player?,
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleControls: () -> Unit,
    onQualityClick: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier,
    videoModifier: Modifier = Modifier,
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
                .padding(bottom = 24.dp) // Margen inferior para curvas y gestos
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp), // Margen lateral amplio para pantallas curvas
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTime(uiState.currentPosition)} / ${formatTime(uiState.duration)}",
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
                value = uiState.currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
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
                        modifier = Modifier.height(2.dp), // Thinner track
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                        )
                    )
                },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        modifier = Modifier.size(12.dp), // Smaller thumb
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
