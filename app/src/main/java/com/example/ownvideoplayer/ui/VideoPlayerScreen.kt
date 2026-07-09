package com.example.ownvideoplayer.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danwolve.ownvideoplayer.player.VideoPlayerViewModel
import com.danwolve.ownvideoplayer.player.VideoPlayerUiState
import com.danwolve.ownvideoplayer.player.VideoQuality
import com.danwolve.ownvideoplayer.player.NotificationInfo
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayer
import com.danwolve.ownvideoplayer.ui.PermissionDeniedPlaceholder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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
            list.add(Manifest.permission.POST_NOTIFICATIONS)
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
                    modifier = Modifier.fillMaxSize(),
                    videoModifier = videoModifier,
                    player = player,
                    uiState = uiState,
                    onPlayPause = { viewModel.playPause() },
                    onRewind = { viewModel.rewind() },
                    onFastForward = { viewModel.fastForward() },
                    onSeek = { viewModel.seekTo(it) },
                    onToggleFullScreen = { viewModel.toggleFullScreen() },
                    onToggleControls = { viewModel.toggleControls() },
                    onQualityClick = { viewModel.setQuality(it) },
                    isControlsEnabled = isControlsEnabled
                )
            } else {
                PermissionDeniedPlaceholder { multiplePermissionsState.launchMultiplePermissionRequest() }
            }
        }
    }
}
