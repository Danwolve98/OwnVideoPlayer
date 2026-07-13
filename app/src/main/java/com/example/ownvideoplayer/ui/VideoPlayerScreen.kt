package com.example.ownvideoplayer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danwolve.ownvideoplayer.player.VideoPlayerViewModel
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayer
import com.danwolve.ownvideoplayer.ui.FullScreenMode
import com.example.ownvideoplayer.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.playerFlow.collectAsState()
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle independent Snackbar for this screen
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage!!)
        }
    }

    // Independent load for this screen (Local Video)
    LaunchedEffect(Unit) {
        viewModel.loadRawResource(R.raw.michael)
    }

    val controller = remember {
        val window = (view.context as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    // Handle System UI (Status/Navigation bars)
    LaunchedEffect(uiState.isFullScreen) {
        if (uiState.isFullScreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Smooth Inset Animation
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()
    val animatedTopPadding by animateDpAsState(
        targetValue = if (uiState.isFullScreen) 0.dp else systemBarsPadding.calculateTopPadding(),
        label = "topPadding"
    )
    val animatedBottomPadding by animateDpAsState(
        targetValue = if (uiState.isFullScreen) 0.dp else systemBarsPadding.calculateBottomPadding(),
        label = "bottomPadding"
    )

    Dialog(
        onDismissRequest = {
            viewModel.setFullScreen(false)
            onDismiss()
        },
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
                .padding(top = animatedTopPadding, bottom = animatedBottomPadding)
                .animateContentSize(),
            contentAlignment = Alignment.Center
        ) {
            OwnVideoPlayer(
                modifier = Modifier.fillMaxSize(),
                player = player,
                uiState = uiState,
                onPlayPause = { viewModel.playPause() },
                onRewind = { viewModel.rewind() },
                onFastForward = { viewModel.fastForward() },
                onSeek = { viewModel.seekTo(it) },
                onToggleFullScreen = { viewModel.toggleFullScreen() }, 
                onToggleControls = { viewModel.toggleControls() },
                onClose = { onDismiss() },
                fullScreenMode = FullScreenMode.SYSTEM_UI
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
