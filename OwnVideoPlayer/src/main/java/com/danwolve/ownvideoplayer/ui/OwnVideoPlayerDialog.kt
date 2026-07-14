package com.danwolve.ownvideoplayer.ui

import androidx.annotation.RawRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danwolve.ownvideoplayer.player.NotificationInfo
import com.danwolve.ownvideoplayer.player.VideoPlayerViewModel

/**
 * Un diálogo de pantalla completa que integra el reproductor de video.
 *
 * @param show Controla la visibilidad del diálogo.
 * @param source La fuente del video (URL o recurso Raw).
 * @param onDismiss Callback cuando el diálogo se cierra.
 * @param notificationInfo Información opcional para la notificación de reproducción.
 */
@Composable
fun OwnVideoPlayerDialog(
    show: Boolean,
    source: VideoSource,
    onDismiss: () -> Unit,
    notificationInfo: NotificationInfo? = null,
) {
    if (!show) return

    val viewModel: VideoPlayerViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.playerFlow.collectAsState()
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Carga inicial del video
    LaunchedEffect(source) {
        when (source) {
            is VideoSource.Url -> viewModel.loadVideo(source.url.toUri(), notificationInfo)
            is VideoSource.Raw -> viewModel.loadRawResource(source.resId, notificationInfo)
        }
        viewModel.setFullScreen(enabled = true)
    }

    // Manejo de errores mediante Snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Manejo de System UI (Barras de estado y navegación)
    val controller = remember {
        val window = (view.context as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    LaunchedEffect(uiState.isFullScreen) {
        if (uiState.isFullScreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Animación suave de los insets
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
            OwnVideoPlayerBase(
                modifier = Modifier.fillMaxSize(),
                player = player,
                uiState = uiState,
                onPlayPause = { viewModel.playPause() },
                onRewind = { viewModel.rewind() },
                onFastForward = { viewModel.fastForward() },
                onSeek = { viewModel.seekTo(it) },
                onToggleFullScreen = { viewModel.toggleFullScreen() },
                onToggleControls = { viewModel.toggleControls() },
                onClose = {
                    viewModel.setFullScreen(false)
                    onDismiss()
                },
                fullScreenMode = FullScreenMode.SYSTEM_UI
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
