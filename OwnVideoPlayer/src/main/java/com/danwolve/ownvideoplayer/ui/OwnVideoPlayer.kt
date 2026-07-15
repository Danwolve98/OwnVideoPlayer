package com.danwolve.ownvideoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import com.danwolve.ownvideoplayer.player.NotificationInfo
import com.danwolve.ownvideoplayer.player.VideoPlayerUiState
import com.danwolve.ownvideoplayer.player.VideoPlayerViewModel
import java.util.Locale

/**
 * Representa la fuente de video para el reproductor.
 */
sealed class VideoSource {
    data class Url(val url: String) : VideoSource()
    data class Raw(@RawRes val resId: Int) : VideoSource()
}

/**
 * Define cómo se comporta el modo de pantalla completa.
 */
enum class FullScreenMode {
    SYSTEM_UI, // Oculta las barras del sistema en la pantalla actual
    DIALOG     // Abre un Diálogo a pantalla completa
}

/**
 * Extensión para encontrar la Activity desde un Context.
 */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Reproductor de video simplificado que gestiona su propio estado y ViewModel.
 * Ideal para integración rápida en cualquier parte de la UI.
 *
 * @param source Fuente del video (URL o Recurso Raw).
 * @param modifier Modificador para el contenedor del reproductor.
 * @param notificationInfo Información opcional para la notificación de medios.
 * @param showControls Si los controles y overlays (error, buffering) están habilitados.
 * @param repeatMode Si el video debe repetirse en bucle al finalizar.
 * @param fullScreenMode Modo de pantalla completa (Diálogo o ocultar UI del sistema).
 */
@Composable
fun OwnVideoPlayer(
    source: VideoSource,
    modifier: Modifier = Modifier,
    notificationInfo: NotificationInfo? = null,
    showControls: Boolean = true,
    repeatMode: Boolean = false,
    fullScreenMode: FullScreenMode = FullScreenMode.SYSTEM_UI
) {
    val viewModel: VideoPlayerViewModel = viewModel(key = source.toString())
    val uiState by viewModel.uiState.collectAsState()
    val player by viewModel.playerFlow.collectAsState()

    LaunchedEffect(source) {
        when (source) {
            is VideoSource.Url -> viewModel.loadVideo(source.url.toUri(), notificationInfo)
            is VideoSource.Raw -> viewModel.loadRawResource(source.resId, notificationInfo)
        }
    }

    LaunchedEffect(repeatMode, player) {
        viewModel.setRepeatMode(repeatMode)
    }

    OwnVideoPlayerBase(
        modifier = modifier,
        player = player,
        uiState = uiState,
        onPlayPause = { viewModel.playPause() },
        onRewind = { viewModel.rewind() },
        onFastForward = { viewModel.fastForward() },
        onSeek = { viewModel.seekTo(it) },
        onToggleFullScreen = { viewModel.toggleFullScreen() },
        onToggleControls = { viewModel.toggleControls() },
        showControls = showControls,
        fullScreenMode = fullScreenMode
    )
}

@Composable
fun OwnVideoPlayerBase(
    modifier: Modifier = Modifier,
    videoModifier: Modifier = Modifier,
    player: Player?,
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onToggleControls: () -> Unit,
    onClose: (() -> Unit)? = null,
    showControls: Boolean = true,
    fullScreenMode: FullScreenMode = FullScreenMode.SYSTEM_UI
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Detener la reproducción automáticamente solo cuando el componente se destruye o se navega fuera
    DisposableEffect(player) {
        onDispose {
            val activity = context.findActivity()
            if (activity == null || !activity.isChangingConfigurations) {
                player?.let {
                    it.pause()
                    it.stop()
                    it.clearMediaItems()
                }
            }
        }
    }
    
    // Manejar el snackbar de error para el modo Diálogo
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null && (fullScreenMode == FullScreenMode.DIALOG && uiState.isFullScreen)) {
            snackbarHostState.showSnackbar(uiState.errorMessage)
        }
    }

    val playerContent = @Composable { isInsideDialog: Boolean ->
        Box(
            modifier = (if (isInsideDialog) Modifier.fillMaxSize() else modifier)
                .background(Color.Black)
                .animateContentSize()
                .then(
                    if (showControls) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleControls
                        )
                    } else Modifier
                )
        ) {
            // Capa de Video: Lógica de ajuste automático
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val containerRatio = maxWidth / maxHeight
                val videoRatio = uiState.videoAspectRatio
                val matchHeight = videoRatio < containerRatio

                Box(
                    modifier = videoModifier
                        .aspectRatio(videoRatio, matchHeight)
                        .animateContentSize()
                ) {
                    player?.let { 
                        PlayerSurface(
                            player = it,
                            modifier = Modifier.fillMaxSize()
                        ) 
                    }
                }
            }

            // Capa de Error para Vista Integrada
            if (showControls && uiState.errorMessage != null && !isInsideDialog && !uiState.isPlaying) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }

            if (showControls && uiState.isBuffering && uiState.errorMessage == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Red
                )
            }

            // Capa de Interfaz de Usuario (UI)
            if (showControls) {
                OwnVideoControlsOverlay(
                    visible = uiState.isControlsVisible,
                    uiState = uiState,
                    onPlayPause = onPlayPause,
                    onRewind = onRewind,
                    onFastForward = onFastForward,
                    onSeek = onSeek,
                    onToggleFullScreen = onToggleFullScreen,
                    onClose = onClose ?: if (uiState.isFullScreen && fullScreenMode == FullScreenMode.DIALOG) onToggleFullScreen else null
                )
            }

            // Host para Snackbar en modo Diálogo
            if (isInsideDialog) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (fullScreenMode == FullScreenMode.DIALOG && uiState.isFullScreen) {
        Dialog(
            onDismissRequest = onToggleFullScreen,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            playerContent(true)
        }
    } else {
        playerContent(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnVideoControlsOverlay(
    visible: Boolean,
    uiState: VideoPlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullScreen: () -> Unit,
    onClose: (() -> Unit)? = null
) {
    var seekingPosition by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val sliderValue = seekingPosition ?: uiState.currentPosition.toFloat()

    val animatedSliderValue by animateFloatAsState(
        targetValue = sliderValue,
        animationSpec = if (isDragging) snap() else tween(durationMillis = 100),
        label = "SliderAnimation"
    )

    LaunchedEffect(uiState.isBuffering, uiState.isPlaying, isDragging, uiState.playbackState) {
        if (!uiState.isBuffering && !isDragging && uiState.playbackState != Player.STATE_BUFFERING) {
            seekingPosition = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                IconButton(onClick = { seekingPosition = (sliderValue - 10000).coerceAtLeast(0f); onRewind() }) {
                    Icon(Icons.Rounded.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onPlayPause) {
                    val playIcon = when {
                        uiState.playbackState == Player.STATE_ENDED -> Icons.Rounded.Replay
                        uiState.playWhenReady -> Icons.Rounded.Pause
                        else -> Icons.Rounded.PlayArrow
                    }
                    Icon(
                        playIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
                IconButton(onClick = { seekingPosition = (sliderValue + 10000).coerceAtMost(uiState.duration.toFloat()); onFastForward() }) {
                    Icon(Icons.Rounded.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${formatTime(animatedSliderValue.toLong())} / ${formatTime(uiState.duration)}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    IconButton(onClick = onToggleFullScreen) {
                        Icon(if (uiState.isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, contentDescription = null, tint = Color.White)
                    }
                }
                Slider(
                    value = animatedSliderValue,
                    onValueChange = { isDragging = true; seekingPosition = it },
                    onValueChangeFinished = { isDragging = false; seekingPosition?.let { onSeek(it.toLong()) } },
                    valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(12.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.Red, activeTrackColor = Color.Red),
                    track = { sliderState ->
                        Box(modifier = Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.CenterStart) {
                            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().background(Color.White.copy(alpha = 0.24f)))
                            val dur = uiState.duration.toFloat().coerceAtLeast(1f)
                            val curr = sliderState.value / dur
                            val buf = uiState.bufferedPosition.toFloat() / dur
                            if (buf > curr) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val startX = curr * size.width
                                    val endX = buf.coerceIn(0f, 1f) * size.width
                                    drawRect(Color.White.copy(alpha = 0.4f), Offset(startX, 0f), Size(endX - startX, size.height))
                                }
                            }
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(2.dp),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.Transparent
                                )
                            )
                        }
                    },
                    thumb = { SliderDefaults.Thumb(interactionSource = remember { MutableInteractionSource() }, modifier = Modifier.size(12.dp), colors = SliderDefaults.colors(thumbColor = Color.Red)) }
                )
            }
        }
    }
}

/**
 * Formatea milisegundos a una cadena de tiempo legible (H:MM:SS o MM:SS).
 */
fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%01d:%02d", minutes, seconds)
    }
}
