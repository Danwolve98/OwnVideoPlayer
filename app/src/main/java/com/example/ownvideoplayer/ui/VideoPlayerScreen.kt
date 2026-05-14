package com.example.ownvideoplayer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import com.example.ownvideoplayer.player.VideoPlayerViewModel
import com.example.ownvideoplayer.player.VideoQuality
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.core.net.toUri

@OptIn( ExperimentalPermissionsApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val player = viewModel.player
    val adaptiveInfo = currentWindowAdaptiveInfo()

    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    if (uiState.isFullScreen) {
        FullScreenPlayerDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.toggleFullScreen() }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (permissionState.status.isGranted) {
                // Adaptive Layout for Tablet/Phone
                if (adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
                    // Horizontal layout for tablets
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .fillMaxHeight()
                                .padding(16.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black)
                        ) {
                            player?.let { PlayerSurface(player = it) }
                            Box(modifier = Modifier.align(Alignment.TopStart)) {
                                PlayerMuteToggle(uiState.isMuted) { viewModel.toggleMute() }
                            }
                            
                            IconButton(
                                onClick = { viewModel.toggleFullScreen() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = "Full Screen", tint = Color.White)
                            }
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(16.dp)
                        ) {
                            PlayerControls(
                                uiState = uiState,
                                onSeek = { viewModel.seekTo(it) },
                                onPlayPause = { viewModel.playPause() },
                                onQualityClick = { viewModel.setQuality(it) },
                                onLoadSample = {
                                    viewModel.loadVideo("https://canarygreendes.grupotecopy.es/sites/default/files/2025-10/guitar-string4_wWK93oAB.mp4".toUri())
                                }
                            )
                        }
                    }
                } else {
                    // Vertical layout for phones
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16 / 9f)
                                .background(Color.Black)
                        ) {
                            player?.let { PlayerSurface(player = it) }
                            Box(modifier = Modifier.align(Alignment.TopStart)) {
                                PlayerMuteToggle(uiState.isMuted) { viewModel.toggleMute() }
                            }
                            
                            IconButton(
                                onClick = { viewModel.toggleFullScreen() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = "Full Screen", tint = Color.White)
                            }
                        }

                        PlayerControls(
                            uiState = uiState,
                            onSeek = { viewModel.seekTo(it) },
                            onPlayPause = { viewModel.playPause() },
                            onQualityClick = { viewModel.setQuality(it) },
                            onLoadSample = {
                                viewModel.loadVideo("https://canarygreendes.grupotecopy.es/sites/default/files/2025-10/guitar-string4_wWK93oAB.mp4".toUri())
                            }
                        )
                    }
                }
            } else {
                PermissionDeniedPlaceholder { permissionState.launchPermissionRequest() }
            }
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
        title = { Text("Select Quality") },
        text = {
            Column {
                qualities.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(quality) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (quality == selectedQuality),
                            onClick = { onQualitySelected(quality) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = quality.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun formatTime(milliseconds: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun FullScreenPlayerDialog(
    viewModel: VideoPlayerViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val player = viewModel.player

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            player?.let { PlayerSurface(player = it) }
            
            // Full screen controls overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(16.dp)
            ) {
                PlayerControls(
                    uiState = uiState,
                    onSeek = { viewModel.seekTo(it) },
                    onPlayPause = { viewModel.playPause() },
                    onQualityClick = { viewModel.setQuality(it) },
                    isFullScreen = true,
                    onExitFullScreen = onDismiss
                )
            }
            
            PlayerMuteToggle(uiState.isMuted) { viewModel.toggleMute() }
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun PlayerMuteToggle(isMuted: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
    ) {
        Icon(
            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Mute/Unmute",
            tint = Color.White
        )
    }
}

@Composable
fun PlayerControls(
    uiState: com.example.ownvideoplayer.player.VideoPlayerUiState,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onQualityClick: (VideoQuality) -> Unit,
    onLoadSample: (() -> Unit)? = null,
    isFullScreen: Boolean = false,
    onExitFullScreen: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(if (isFullScreen) 0.dp else 16.dp)
            .then(
                if (!isFullScreen) Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
                else Modifier
            )
    ) {
        // Progress and Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(uiState.currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFullScreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = uiState.currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (isFullScreen) Color.White else MaterialTheme.colorScheme.primary,
                    activeTrackColor = if (isFullScreen) Color.White else MaterialTheme.colorScheme.primary,
                )
            )
            
            Text(
                text = formatTime(uiState.duration),
                style = MaterialTheme.typography.labelMedium,
                color = if (isFullScreen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(if (isFullScreen) 72.dp else 64.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isFullScreen) Color.White else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isFullScreen) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(if (isFullScreen) 40.dp else 32.dp)
                )
            }

            var showQualityDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showQualityDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isFullScreen) Color.White else MaterialTheme.colorScheme.primary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (isFullScreen) Color.White else MaterialTheme.colorScheme.outline)
                )
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiState.selectedQuality?.label ?: "Auto")
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
            
            if (isFullScreen && onExitFullScreen != null) {
                IconButton(onClick = onExitFullScreen) {
                    Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Full Screen", tint = Color.White)
                }
            }
        }

        onLoadSample?.let {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Stream Sample Video")
            }
        }
    }
}

@Composable
fun PermissionDeniedPlaceholder(onGrant: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Storage access is needed to play local videos.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrant) {
                Text("Grant Permission")
            }
        }
    }
}
